/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.impl.wideevents

import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiSessionCallback
import com.duckduckgo.duckchat.api.DuckAiSessionExitTrigger
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * All work happens on one sequential consumer reading from one [Channel], so there is never more than
 * one caller's event being processed at a time, and — crucially — events are processed in the order
 * they were sent. That ordering is what makes [DuckAiSessionCallback.onExitIntent] safe to call for a
 * tab whose session is still starting: `onLaunchLandingResolved`/`onDuckAiPageVisible`/
 * `onSelectedTabChanged` for that tab can only have been sent first (the user can't act on a tab before
 * it becomes visible), so the consumer always finishes starting the session before it looks at the exit
 * intent, even if starting is slow.
 *
 * This class is the sole owner of the session's status/reason mapping and the only caller of
 * [WideEventClient.flowFinish].
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DuckAiSessionCallback::class)
@ContributesMultibinding(AppScope::class, boundType = BrowserLifecycleObserver::class)
class RealDuckAiSessionWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val duckChat: DuckChat,
    private val duckChatFeature: Lazy<DuckChatFeature>,
    dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : DuckAiSessionCallback, BrowserLifecycleObserver {

    private val coroutineScope = CoroutineScope(appCoroutineScope.coroutineContext + dispatchers.io())
    private val channel = Channel<Action>(capacity = Channel.UNLIMITED)

    // Touched only by the single consumer loop below — no mutex or atomics needed.
    private var activeSession: SessionState? = null
    private var lastSelectedTab: TabState? = null
    private var pendingExit: PendingExit? = null

    // Both default closed so nothing can start a session before the app has genuinely opened once.
    // AppClosed clears both, so a WebView callback or tab update that was already in flight when the
    // app closed — and only reaches the channel afterwards — can't start a new session while nothing is
    // visible; a session can only start again once the next app-open's landing has resolved.
    private var appOpen = false
    private var launchResolved = false

    init {
        coroutineScope.launch {
            for (action in channel) {
                try {
                    processAction(action)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(tag = TAG) { "Error processing ${action::class.simpleName}: ${e.message}" }
                }
            }
        }
    }

    override fun onLaunchLandingResolved(tabId: String?, url: String?) {
        channel.trySend(Action.LaunchLandingResolved(tabId, url))
    }

    override fun onDuckAiPageVisible(tabId: String, url: String) {
        channel.trySend(Action.DuckAiPageVisible(tabId, url))
    }

    override fun onSelectedTabChanged(tabId: String?, url: String?) {
        channel.trySend(Action.SelectedTabChanged(tabId, url))
    }

    override fun onExitIntent(tabId: String, trigger: DuckAiSessionExitTrigger) {
        channel.trySend(Action.ExitIntent(tabId, trigger))
    }

    override fun onPromptSubmitted(tabId: String) {
        channel.trySend(Action.PromptSubmitted(tabId))
    }

    override fun onNewChatCreated(tabId: String) {
        channel.trySend(Action.NewChatCreated(tabId))
    }

    override fun onOpen(isFreshLaunch: Boolean) {
        channel.trySend(Action.AppOpened)
    }

    override fun onClose() {
        channel.trySend(Action.AppClosed)
    }

    private suspend fun processAction(action: Action) {
        // Transition detection and app-open/closed bookkeeping always run, even while the feature is
        // disabled, so stale state doesn't cause a missed start — or a spurious one — right after the
        // feature is re-enabled. Only acting on a detected transition (or any other action) is gated on
        // the feature being enabled.
        if (action is Action.SelectedTabChanged) {
            val current = computeTabState(action.tabId, action.url)
            val previous = lastSelectedTab
            lastSelectedTab = current
            if (previous == null || current == previous) return
            if (isFeatureDisabled()) {
                abortActiveSession()
                return
            }
            applySelectedTabTransition(previous, current)
            return
        }

        if (action is Action.AppOpened) {
            appOpen = true
            return
        }

        // Every other Action subtype is a Dispatchable, so this when below is exhaustive without
        // needing (unreachable) branches for the two subtypes already handled above.
        check(action is Action.Dispatchable)

        if (action is Action.AppClosed) {
            appOpen = false
            launchResolved = false
        }

        if (isFeatureDisabled()) {
            abortActiveSession()
            return
        }

        when (action) {
            is Action.LaunchLandingResolved -> processLaunchLandingResolved(action.tabId, action.url)
            is Action.DuckAiPageVisible -> processDuckAiPageVisible(action.tabId, action.url)
            is Action.ExitIntent -> processExitIntent(action.tabId, action.trigger)
            is Action.PromptSubmitted -> recordActivityOnce(
                STEP_PROMPT_SUBMITTED,
                action.tabId,
                isAlreadyRecorded = { it.promptSubmitted },
            ) { it.promptSubmitted = true }
            is Action.NewChatCreated -> recordActivityOnce(
                STEP_NEW_CHAT_CREATED,
                action.tabId,
                isAlreadyRecorded = { it.newChatCreated },
            ) { it.newChatCreated = true }
            Action.AppClosed -> processAppClosed()
        }
    }

    private suspend fun processLaunchLandingResolved(tabId: String?, url: String?) {
        if (!appOpen) return
        launchResolved = true
        if (tabId == null) return
        tryStartSession(tabId, url)
    }

    private suspend fun processDuckAiPageVisible(tabId: String, url: String) {
        if (!appOpen || !launchResolved) return
        tryStartSession(tabId, url)
    }

    private suspend fun tryStartSession(tabId: String, url: String?) {
        if (activeSession != null) return
        val state = computeTabState(tabId, url)
        if (!state.isDuckAi) return
        startSession(tabId, state.chatId)
    }

    private suspend fun applySelectedTabTransition(previous: TabState, current: TabState) {
        when {
            previous.isDuckAi && current.tabId != previous.tabId -> {
                val trigger = resolvePendingExit(previous.tabId!!) ?: DuckAiSessionExitTrigger.TAB_SWITCHED
                endSessionIfMatches(previous.tabId, trigger)
                if (current.isDuckAi && current.tabId != null) {
                    startSessionIfNoneActive(current.tabId, current.chatId)
                }
            }
            // The two branches below are only reached once the tab identifier is known to be
            // unchanged, since the branch above already handles every case where it differs.
            previous.isDuckAi && !current.isDuckAi -> {
                val trigger = resolvePendingExit(previous.tabId!!) ?: DuckAiSessionExitTrigger.OTHER_NAVIGATION
                endSessionIfMatches(previous.tabId, trigger)
            }
            previous.isDuckAi && current.isDuckAi -> {
                // The URL changed but Duck.ai is still showing in the same tab (a link within the chat,
                // or Back landing on another Duck.ai page): any pending exit for it no longer applies,
                // and the active session's chat identity may have changed.
                resolvePendingExit(previous.tabId!!)
                updateChatId(previous.tabId, current.chatId)
            }
            // previous.isDuckAi is guaranteed false here, since it's covered by the three branches above.
            current.isDuckAi && current.tabId != null -> {
                startSessionIfNoneActive(current.tabId, current.chatId)
            }
        }
    }

    private fun processExitIntent(tabId: String, trigger: DuckAiSessionExitTrigger) {
        if (activeSession?.tabId != tabId) return
        pendingExit = PendingExit(tabId, trigger)
    }

    private suspend fun processAppClosed() {
        val session = activeSession ?: run {
            pendingExit = null
            return
        }
        activeSession = null

        val pending = pendingExit
        pendingExit = null
        val backOrCloseTrigger = pending
            ?.takeIf { it.tabId == session.tabId && it.trigger == DuckAiSessionExitTrigger.BACK_OR_CLOSE }
            ?.trigger

        if (backOrCloseTrigger != null) {
            finishSession(session, FlowStatus.Success, REASON_LEFT_DUCKAI, backOrCloseTrigger)
        } else {
            finishSession(session, FlowStatus.Cancelled, REASON_APP_BACKGROUNDED, exitTrigger = null)
        }
    }

    private fun resolvePendingExit(tabId: String): DuckAiSessionExitTrigger? {
        val current = pendingExit ?: return null
        if (current.tabId != tabId) return null
        pendingExit = null
        return current.trigger
    }

    private suspend fun startSessionIfNoneActive(tabId: String, chatId: String?) {
        // launchResolved requires a resolved landing (see onLaunchLandingResolved) before a tab switch
        // alone is trusted to start a session — otherwise stale pre-launch tab state could start one
        // before the app has actually decided where it's landing.
        if (!appOpen || !launchResolved) return
        if (activeSession != null) return
        startSession(tabId, chatId)
    }

    private suspend fun endSessionIfMatches(tabId: String, trigger: DuckAiSessionExitTrigger) {
        val session = activeSession ?: return
        if (session.tabId != tabId) return
        activeSession = null
        finishSession(session, FlowStatus.Success, REASON_LEFT_DUCKAI, trigger)
    }

    private suspend fun updateChatId(tabId: String, chatId: String?) {
        val session = activeSession ?: return
        if (session.tabId != tabId) return
        if (chatId == session.lastObservedChatId) return

        session.lastObservedChatId = chatId
        if (session.chatIdChangeRecorded) return

        session.chatIdChangeRecorded = true
        wideEventClient.flowStep(wideEventId = session.flowId, stepName = STEP_CHAT_ID_CHANGED)
        logcat(tag = TAG) { "Duck.ai session: $STEP_CHAT_ID_CHANGED recorded" }
    }

    private suspend fun recordActivityOnce(
        stepName: String,
        tabId: String,
        isAlreadyRecorded: (SessionState) -> Boolean,
        markRecorded: (SessionState) -> Unit,
    ) {
        val session = activeSession ?: return
        if (session.tabId != tabId) return
        if (isAlreadyRecorded(session)) return

        markRecorded(session)
        wideEventClient.flowStep(wideEventId = session.flowId, stepName = stepName)
        logcat(tag = TAG) { "Duck.ai session: $stepName recorded" }
    }

    private suspend fun startSession(tabId: String, chatId: String?) {
        val result = wideEventClient.flowStart(
            name = FEATURE_NAME,
            cleanupPolicy = CleanupPolicy.OnProcessStart(
                ignoreIfIntervalTimeoutPresent = false,
                flowStatus = FlowStatus.Unknown,
            ),
            metadata = mapOf(KEY_STATUS_REASON to REASON_APP_TERMINATED),
        )
        result.onSuccess { flowId ->
            activeSession = SessionState(flowId = flowId, tabId = tabId, lastObservedChatId = chatId)
            logcat(tag = TAG) { "Duck.ai session started" }
        }.onFailure { error ->
            logcat(tag = TAG) { "Failed to start Duck.ai session: ${error.message}" }
        }
    }

    private suspend fun finishSession(
        session: SessionState,
        status: FlowStatus,
        statusReason: String,
        exitTrigger: DuckAiSessionExitTrigger?,
    ) {
        wideEventClient.flowFinish(
            wideEventId = session.flowId,
            status = status,
            metadata = buildMap {
                put(KEY_STATUS_REASON, statusReason)
                exitTrigger?.let { put(KEY_EXIT_TRIGGER, it.toPixelValue()) }
            },
        )
        logcat(tag = TAG) { "Duck.ai session finished: status=$status, reason=$statusReason" }
    }

    private suspend fun abortActiveSession() {
        pendingExit = null
        activeSession?.let { session ->
            wideEventClient.flowAbort(session.flowId)
            activeSession = null
        }
    }

    private fun isFeatureDisabled(): Boolean = !duckChatFeature.get().sendDuckAiSessionWideEvent().isEnabled()

    private fun computeTabState(tabId: String?, url: String?): TabState {
        val nonBlankUrl = url?.takeIf { it.isNotBlank() }
        val uri = nonBlankUrl?.toUri()
        val isDuckAi = uri != null && duckChat.isDuckChatUrl(uri)
        val chatId = uri?.toChatIdOrNull(duckChat)
        return TabState(tabId, nonBlankUrl, isDuckAi, chatId)
    }

    private sealed class Action {
        // Actions gated by isFeatureDisabled() and dispatched by the second `when` in processAction —
        // everything except the two that need to run before, and regardless of, that check.
        sealed class Dispatchable : Action()
        data class LaunchLandingResolved(val tabId: String?, val url: String?) : Dispatchable()
        data class DuckAiPageVisible(val tabId: String, val url: String) : Dispatchable()
        data class ExitIntent(val tabId: String, val trigger: DuckAiSessionExitTrigger) : Dispatchable()
        data class PromptSubmitted(val tabId: String) : Dispatchable()
        data class NewChatCreated(val tabId: String) : Dispatchable()
        data object AppClosed : Dispatchable()

        data class SelectedTabChanged(val tabId: String?, val url: String?) : Action()
        data object AppOpened : Action()
    }

    private data class TabState(
        val tabId: String?,
        val url: String?,
        val isDuckAi: Boolean,
        val chatId: String?,
    )

    private data class PendingExit(
        val tabId: String,
        val trigger: DuckAiSessionExitTrigger,
    )

    private class SessionState(
        val flowId: Long,
        val tabId: String,
        var promptSubmitted: Boolean = false,
        var newChatCreated: Boolean = false,
        var chatIdChangeRecorded: Boolean = false,
        var lastObservedChatId: String? = null,
    )

    private companion object {
        const val TAG = "RealDuckAiSessionWideEvent"
        const val FEATURE_NAME = "duckai_session"

        const val REASON_LEFT_DUCKAI = "left_duckai"
        const val REASON_APP_BACKGROUNDED = "app_backgrounded"
        const val REASON_APP_TERMINATED = "app_terminated"

        const val KEY_STATUS_REASON = "status_reason"
        const val KEY_EXIT_TRIGGER = "exit_trigger"

        const val STEP_PROMPT_SUBMITTED = "prompt_submitted"
        const val STEP_NEW_CHAT_CREATED = "new_chat_created"
        const val STEP_CHAT_ID_CHANGED = "chat_id_changed"
    }
}

private fun DuckAiSessionExitTrigger.toPixelValue(): String = name.lowercase()
