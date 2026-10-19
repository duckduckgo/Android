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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface DuckAiResponseWaitWideEvent {
    fun onNativePromptSubmitted(tabId: String, reasoningEffort: String?, selectedTool: String?)
    fun onResponseState(tabId: String, status: String)
    fun onStopTapped(tabId: String)
}

/**
 * Measures whether the user waits for a Duck.ai response or leaves and comes back for it.
 *
 * Every call is queued on one [Channel] and handled by a single consumer, so events are applied in the
 * order they were sent and the state below is never touched concurrently.
 *
 * A flow is identified by its chat ID; until the page reports one it is identified by its tab. Which chat
 * is on screen comes from this class's own tracking of the selected tab, so callers pass only a tab ID.
 *
 * Open flows are saved to [DuckAiResponseWaitFlowStore] after every change and restored as the consumer's first
 * action, so a wait that outlives the process can still record the user's return.
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DuckAiResponseWaitWideEvent::class)
@ContributesMultibinding(AppScope::class, boundType = BrowserLifecycleObserver::class)
class DuckAiResponseWaitWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val duckChat: DuckChat,
    private val duckChatFeature: Lazy<DuckChatFeature>,
    private val flowStore: DuckAiResponseWaitFlowStore,
    dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : DuckAiResponseWaitWideEvent, DuckAiSessionCallback, BrowserLifecycleObserver {

    private val coroutineScope = CoroutineScope(appCoroutineScope.coroutineContext + dispatchers.io())
    private val channel = Channel<Action>(capacity = Channel.UNLIMITED)

    private val flows = mutableListOf<ResponseWait>()
    private var persisted = emptyList<PersistedResponseWaitFlow>()
    private var selectedTab: TabState? = null
    private var pendingExit: PendingExit? = null
    private var newChatTabId: String? = null

    // Both default closed so nothing counts as on screen until the app has opened and resolved its landing.
    private var appOpen = false
    private var launchResolved = false

    init {
        // Queued before anything else can reach the channel, so restored flows exist before any callback is applied.
        channel.trySend(Action.Recover)
        coroutineScope.launch {
            for (action in channel) {
                logFailure("processing ${action::class.simpleName}") { processAction(action) }
                logFailure("saving open flows") { persist() }
            }
        }
    }

    private suspend fun logFailure(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(tag = TAG) { "Error $what: ${e.message}" }
        }
    }

    override fun onNativePromptSubmitted(tabId: String, reasoningEffort: String?, selectedTool: String?) {
        channel.trySend(Action.NativePromptSubmitted(tabId, reasoningEffort, selectedTool))
    }

    override fun onResponseState(tabId: String, status: String) {
        channel.trySend(Action.ResponseState(tabId, status))
    }

    override fun onStopTapped(tabId: String) {
        channel.trySend(Action.StopTapped(tabId))
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

    // Flows start only from onNativePromptSubmitted, which carries the reasoning and tool metadata the start needs.
    override fun onPromptSubmitted(tabId: String) = Unit

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
        if (action is Action.Recover) return recover()
        val previousTab = selectedTab
        // Presence is tracked even while the feature is off, so re-enabling it starts from the real screen state.
        updatePresence(action)
        if (isFeatureDisabled()) {
            abortAllFlows()
        } else {
            if (action is Action.AppOpened) dropFlowsClosedByCleanup()
            when (action) {
                is Action.NativePromptSubmitted -> startFlow(action.tabId, action.reasoningEffort, action.selectedTool)
                is Action.ResponseState -> applyResponseState(action.tabId, action.status)
                is Action.StopTapped -> applyStop(action.tabId)
                is Action.NewChatCreated -> {
                    leaveForNewChat(action.tabId)
                    reconcileOnScreen(appClosed = false)
                }
                else -> reconcileOnScreen(appClosed = action is Action.AppClosed)
            }
        }
        if (action is Action.AppClosed) {
            pendingExit = null
            newChatTabId = null
        }
        val previousTabId = previousTab?.tabId
        if (previousTabId != null && previousTab != selectedTab) clearIntents(previousTabId)
    }

    private suspend fun recover() {
        persisted = flowStore.load()
        flows += persisted.map { it.restore() }
        dropFlowsClosedByCleanup()
        if (isFeatureDisabled()) {
            abortAllFlows()
        } else {
            // A flow that never left died on screen; the reloaded page's first ready would wrongly finish it as watched.
            flows.filter { !it.left }.forEach { abort(it) }
            flows.filter { it.returned && it.done }.forEach { finish(it, REASON_RETURNED) }
        }
    }

    // The 24h cleanup closes flows on its own; a flow it closed must not receive more steps.
    private suspend fun dropFlowsClosedByCleanup() {
        if (flows.isEmpty()) return
        // Kept on failure: a transient error or the wide-events kill switch must not drop flows that are still open.
        val openIds = wideEventClient.getFlowIds(FEATURE_NAME).getOrNull() ?: return
        flows.removeAll { it.flowId !in openIds }
    }

    private suspend fun persist() {
        val snapshot = flows.map { it.toPersisted() }
        if (snapshot == persisted) return
        flowStore.save(snapshot)
        // Only after a successful save, so a failed write is retried after the next action.
        persisted = snapshot
    }

    private fun updatePresence(action: Action) {
        when (action) {
            Action.AppOpened -> appOpen = true
            Action.AppClosed -> {
                appOpen = false
                launchResolved = false
            }
            is Action.LaunchLandingResolved -> if (appOpen) {
                launchResolved = true
                // A null tab means the landing isn't Duck.ai, so a Duck.ai tab selected before the app closed is no longer on screen.
                selectedTab = action.tabId?.let { computeTabState(it, action.url) }
            }
            is Action.DuckAiPageVisible -> if (appOpen && launchResolved) selectedTab = computeTabState(action.tabId, action.url)
            is Action.SelectedTabChanged -> selectedTab = computeTabState(action.tabId, action.url)
            is Action.ExitIntent -> if (pendingExit == null) pendingExit = PendingExit(action.tabId, action.trigger)
            is Action.NewChatCreated -> newChatTabId = action.tabId
            else -> Unit
        }
    }

    private fun clearIntents(tabId: String) {
        if (pendingExit?.tabId == tabId) pendingExit = null
        if (newChatTabId == tabId) newChatTabId = null
    }

    private fun onScreen(): OnScreen? {
        val tab = selectedTab ?: return null
        val tabId = tab.tabId ?: return null
        if (!appOpen || !launchResolved || !tab.isDuckAi) return null
        return OnScreen(tabId, tab.chatId)
    }

    private suspend fun reconcileOnScreen(appClosed: Boolean) {
        val screen = onScreen()
        for (flow in flows.toList()) {
            if (screen != null && flow.matches(screen)) {
                flow.tabId = screen.tabId
                if (flow.chatId == null) flow.chatId = screen.chatId
                if (!flow.onScreen) {
                    flow.onScreen = true
                    if (flow.left) recordReturn(flow)
                }
            } else {
                if (flow.onScreen) {
                    flow.onScreen = false
                    recordLeave(flow, resolveLeftVia(flow, appClosed))
                }
                // The tab now shows another chat, so its response states no longer describe this flow.
                if (screen?.tabId == flow.tabId) flow.chatSwitchedAway = true
            }
        }
    }

    // With history off the URL never carries a chat ID, so a new chat in the same tab looks unchanged to reconcileOnScreen.
    // A pending exit means the new chat opens in another tab, and the tab switch records that leave instead.
    private suspend fun leaveForNewChat(tabId: String) {
        if (pendingExit?.tabId == tabId) return
        val flow = flows.firstOrNull { it.onScreen && it.chatId == null && it.tabId == tabId } ?: return
        newChatTabId = null
        flow.onScreen = false
        flow.chatSwitchedAway = true
        recordLeave(flow, LEFT_VIA_CHAT_SWITCHED)
    }

    private fun resolveLeftVia(flow: ResponseWait, appClosed: Boolean): String {
        val current = selectedTab
        val sameTab = current?.tabId == flow.tabId
        val pendingTrigger = pendingExit?.takeIf { it.tabId == flow.tabId }?.trigger
        return when {
            appClosed -> if (pendingTrigger == DuckAiSessionExitTrigger.BACK_OR_CLOSE) LEFT_VIA_BACK_OR_CLOSE else LEFT_VIA_APP_BACKGROUNDED
            // The flow only leaves when it no longer matches, so the same tab still on Duck.ai shows another or a new chat.
            sameTab && (current?.isDuckAi == true || newChatTabId == flow.tabId) -> LEFT_VIA_CHAT_SWITCHED
            else -> pendingTrigger?.name?.lowercase() ?: if (sameTab) LEFT_VIA_OTHER_NAVIGATION else LEFT_VIA_TAB_SWITCHED
        }
    }

    private suspend fun recordLeave(flow: ResponseWait, leftVia: String) {
        when {
            flow.done && flow.returned -> finish(flow, REASON_RETURNED)
            !flow.done && !flow.left -> {
                flow.left = true
                wideEventClient.flowStep(flow.flowId, STEP_LEFT, metadata = mapOf(KEY_LEFT_VIA to leftVia))
                wideEventClient.intervalEnd(flow.flowId, INTERVAL_PROMPT_TO_LEAVE)
                wideEventClient.intervalStart(flow.flowId, INTERVAL_LEAVE_TO_RETURN, buckets = LEAVE_BUCKETS)
            }
        }
    }

    private suspend fun recordReturn(flow: ResponseWait) {
        flow.chatSwitchedAway = false
        if (!flow.returned) {
            flow.returned = true
            val stateOnReturn = if (flow.pageReloaded) STATE_PAGE_RELOADED else flow.lastState
            wideEventClient.flowStep(flow.flowId, STEP_RETURNED, metadata = mapOf(KEY_RESPONSE_STATE_ON_RETURN to stateOnReturn))
            wideEventClient.intervalEnd(flow.flowId, INTERVAL_LEAVE_TO_RETURN)
        }
        if (flow.readyToReturnRunning) {
            flow.readyToReturnRunning = false
            wideEventClient.intervalEnd(flow.flowId, INTERVAL_READY_TO_RETURN)
        }
    }

    private suspend fun startFlow(tabId: String, reasoningEffort: String?, selectedTool: String?) {
        flows.firstOrNull { it.onScreen && it.tabId == tabId && it.returned }?.let { returned ->
            wideEventClient.flowStep(returned.flowId, STEP_PROMPT_AFTER_RETURN)
            finish(returned, REASON_RETURNED)
        }
        // The outside-chat and in-chat paths can both report the same prompt.
        if (flows.any { it.tabId == tabId && !it.left && !it.done }) return

        val chatId = selectedTab?.takeIf { it.tabId == tabId }?.chatId
        wideEventClient.flowStart(
            name = FEATURE_NAME,
            metadata = startMetadata(reasoningEffort, selectedTool),
            cleanupPolicy = CleanupPolicy.OnTimeout(duration = 24.hours, flowStatus = FlowStatus.Failure(reason = REASON_NOT_RETURNED)),
        ).onSuccess { flowId ->
            val flow = ResponseWait(flowId = flowId, tabId = tabId, chatId = chatId, awaitingFirstStream = true)
            flow.onScreen = onScreen()?.let(flow::matches) ?: false
            flows += flow
            wideEventClient.intervalStart(flowId, INTERVAL_PROMPT_TO_LEAVE, buckets = PROMPT_BUCKETS)
            logcat(tag = TAG) { "Duck.ai response wait started" }
        }.onFailure { error ->
            logcat(tag = TAG) { "Failed to start Duck.ai response wait: ${error.message}" }
        }
    }

    private fun startMetadata(reasoningEffort: String?, selectedTool: String?): Map<String, String> = buildMap {
        val effort = reasoningEffort?.takeIf { it in REASONING_EFFORTS }
        effort?.let { put(KEY_REASONING_EFFORT, it) }
        put(KEY_USED_REASONING, (effort in REASONING_USED).toString())
        put(KEY_USED_EXTENDED_REASONING, (effort in EXTENDED_REASONING_USED).toString())
        put(KEY_IMAGE_GENERATION, (selectedTool == TOOL_GENERATE_IMAGE).toString())
        put(KEY_WEB_SEARCH, (selectedTool == TOOL_WEB_SEARCH).toString())
        put(KEY_STATUS_REASON, REASON_NOT_RETURNED)
        put(KEY_RESPONSE_RESULT, RESULT_UNKNOWN)
    }

    private suspend fun applyResponseState(tabId: String, status: String) {
        // The off-screen match also covers an outside-chat prompt whose page streams before the tab reports it as visible.
        val flow = flows.firstOrNull { it.onScreen && it.tabId == tabId }
            ?: flows.firstOrNull { it.tabId == tabId && !it.onScreen && !it.chatSwitchedAway }
            ?: return

        when {
            status == STATUS_BLOCKED -> {
                abort(flow)
                return
            }
            status in LOADING_STATUSES || status.startsWith(START_STREAM_PREFIX) -> {
                flow.lastState = STATE_LOADING
                flow.awaitingReloadedStream = false
                flow.awaitingFirstStream = false
                if (flow.result != RESULT_STOPPED) flow.done = false
                return
            }
            status == STATUS_READY || status == STATUS_ERROR -> {
                if (flow.awaitingFirstStream && status == STATUS_READY) return
                flow.lastState = status
                // The reloaded page reports ready even for a response that failed before the process died.
                val keepsError = flow.awaitingReloadedStream && flow.result == RESULT_ERROR && status == STATUS_READY
                if (flow.result != RESULT_STOPPED && !keepsError) flow.result = status
                flow.done = true
            }
            else -> return
        }

        if (!flow.left) {
            finish(flow, REASON_WATCHED)
        } else if (!flow.onScreen && status == STATUS_READY && !flow.readyWhileAwayRecorded) {
            // shortcut: an error while away has no step to carry it, so a flow that ends not_returned keeps
            // response_result=unknown; add a dedicated step if the data turns out to need it.
            flow.readyWhileAwayRecorded = true
            flow.readyToReturnRunning = true
            wideEventClient.flowStep(flow.flowId, STEP_READY_WHILE_AWAY, metadata = mapOf(KEY_RESPONSE_RESULT to RESULT_READY))
            wideEventClient.intervalStart(flow.flowId, INTERVAL_READY_TO_RETURN, buckets = LEAVE_BUCKETS)
        }
    }

    private suspend fun applyStop(tabId: String) {
        val flow = flows.firstOrNull { it.onScreen && it.tabId == tabId } ?: return
        flow.result = RESULT_STOPPED
        flow.done = true
        if (!flow.left) finish(flow, REASON_WATCHED)
    }

    private suspend fun finish(flow: ResponseWait, statusReason: String) {
        flows.remove(flow)
        wideEventClient.flowFinish(
            wideEventId = flow.flowId,
            status = FlowStatus.Success,
            metadata = mapOf(KEY_STATUS_REASON to statusReason, KEY_RESPONSE_RESULT to flow.result),
        )
        logcat(tag = TAG) { "Duck.ai response wait finished: reason=$statusReason, result=${flow.result}" }
    }

    private suspend fun abort(flow: ResponseWait) {
        flows.remove(flow)
        wideEventClient.flowAbort(flow.flowId)
    }

    private suspend fun abortAllFlows() {
        flows.toList().forEach { abort(it) }
    }

    private fun isFeatureDisabled(): Boolean = !duckChatFeature.get().sendDuckAiResponseWaitWideEvent().isEnabled()

    private fun computeTabState(tabId: String?, url: String?): TabState {
        val uri = url?.takeIf { it.isNotBlank() }?.toUri()
        val isDuckAi = uri != null && duckChat.isDuckChatUrl(uri)
        return TabState(tabId, url, isDuckAi, uri?.toChatIdOrNull(duckChat))
    }

    private sealed class Action {
        data class NativePromptSubmitted(val tabId: String, val reasoningEffort: String?, val selectedTool: String?) : Action()
        data class ResponseState(val tabId: String, val status: String) : Action()
        data class StopTapped(val tabId: String) : Action()
        data class LaunchLandingResolved(val tabId: String?, val url: String?) : Action()
        data class DuckAiPageVisible(val tabId: String, val url: String) : Action()
        data class SelectedTabChanged(val tabId: String?, val url: String?) : Action()
        data class ExitIntent(val tabId: String, val trigger: DuckAiSessionExitTrigger) : Action()
        data class NewChatCreated(val tabId: String) : Action()
        data object AppOpened : Action()
        data object AppClosed : Action()
        data object Recover : Action()
    }

    private data class TabState(
        val tabId: String?,
        val url: String?,
        val isDuckAi: Boolean,
        val chatId: String?,
    )

    private data class OnScreen(val tabId: String, val chatId: String?)

    private data class PendingExit(val tabId: String, val trigger: DuckAiSessionExitTrigger)

    private class ResponseWait(
        val flowId: Long,
        var tabId: String,
        var chatId: String?,
        // A page loading for an outside-chat prompt reports an idle ready before the prompt's stream starts.
        // An error is still accepted, since a request can fail before any stream starts.
        var awaitingFirstStream: Boolean = false,
    ) {
        var onScreen = false
        var left = false
        var returned = false
        var done = false
        var result = RESULT_UNKNOWN
        var lastState = STATE_LOADING
        var readyWhileAwayRecorded = false
        var readyToReturnRunning = false

        // Set once the flow's tab shows a different chat, so tab-keyed matching and response states stop applying to it.
        var chatSwitchedAway = false

        // Restored after process death: the reloaded page starts from scratch, so its state on return says nothing yet.
        var pageReloaded = false

        // Restored and no stream seen since, so a ready from the reloaded page can't be trusted to replace an error.
        var awaitingReloadedStream = false

        fun toPersisted() = PersistedResponseWaitFlow(
            flowId = flowId,
            tabId = tabId,
            chatId = chatId,
            left = left,
            returned = returned,
            done = done,
            result = result,
            readyWhileAwayRecorded = readyWhileAwayRecorded,
            readyToReturnRunning = readyToReturnRunning,
            chatSwitchedAway = chatSwitchedAway,
        )

        fun matches(screen: OnScreen): Boolean =
            if (chatId != null) chatId == screen.chatId else tabId == screen.tabId && !chatSwitchedAway
    }

    private fun PersistedResponseWaitFlow.restore() = ResponseWait(flowId = flowId, tabId = tabId, chatId = chatId).also {
        it.left = left
        it.returned = returned
        it.done = done
        it.result = result
        it.readyWhileAwayRecorded = readyWhileAwayRecorded
        it.readyToReturnRunning = readyToReturnRunning
        it.chatSwitchedAway = chatSwitchedAway
        it.pageReloaded = true
        it.awaitingReloadedStream = true
    }

    private companion object {
        const val TAG = "DuckAiResponseWaitWideEvent"
        const val FEATURE_NAME = "duckai-response-wait"

        const val KEY_REASONING_EFFORT = "reasoning_effort"
        const val KEY_USED_REASONING = "used_reasoning"
        const val KEY_USED_EXTENDED_REASONING = "used_extended_reasoning"
        const val KEY_IMAGE_GENERATION = "user_requested_image_generation"
        const val KEY_WEB_SEARCH = "user_requested_web_search"
        const val KEY_STATUS_REASON = "status_reason"
        const val KEY_RESPONSE_RESULT = "response_result"
        const val KEY_LEFT_VIA = "left_via"
        const val KEY_RESPONSE_STATE_ON_RETURN = "response_state_on_return"

        const val STEP_LEFT = "left"
        const val STEP_RETURNED = "returned"
        const val STEP_READY_WHILE_AWAY = "response_ready_while_away"
        const val STEP_PROMPT_AFTER_RETURN = "prompt_after_return"

        const val INTERVAL_PROMPT_TO_LEAVE = "prompt_to_leave_ms_bucketed"
        const val INTERVAL_LEAVE_TO_RETURN = "leave_to_return_ms_bucketed"
        const val INTERVAL_READY_TO_RETURN = "ready_to_return_ms_bucketed"

        const val REASON_WATCHED = "watched"
        const val REASON_RETURNED = "returned"
        const val REASON_NOT_RETURNED = "not_returned"

        const val RESULT_READY = "ready"
        const val RESULT_ERROR = "error"
        const val RESULT_STOPPED = "stopped"
        const val RESULT_UNKNOWN = "unknown"

        const val STATE_LOADING = "loading"
        const val STATE_PAGE_RELOADED = "page_reloaded"

        const val LEFT_VIA_APP_BACKGROUNDED = "app_backgrounded"
        const val LEFT_VIA_BACK_OR_CLOSE = "back_or_close"
        const val LEFT_VIA_CHAT_SWITCHED = "chat_switched"
        const val LEFT_VIA_TAB_SWITCHED = "tab_switched"
        const val LEFT_VIA_OTHER_NAVIGATION = "other_navigation"

        const val STATUS_READY = "ready"
        const val STATUS_ERROR = "error"
        const val STATUS_BLOCKED = "blocked"
        const val START_STREAM_PREFIX = "start_stream:"
        val LOADING_STATUSES = setOf("loading", "streaming")

        const val TOOL_GENERATE_IMAGE = "GenerateImage"
        const val TOOL_WEB_SEARCH = "WebSearch"

        val REASONING_EFFORTS = setOf("none", "minimal", "low", "medium", "high")
        val REASONING_USED = setOf("low", "medium", "high")
        val EXTENDED_REASONING_USED = setOf("medium", "high")

        val PROMPT_BUCKETS = setOf(0.seconds, 2.seconds, 5.seconds, 10.seconds, 20.seconds, 30.seconds)
        val LEAVE_BUCKETS = setOf(0.seconds, 5.seconds, 10.seconds, 30.seconds, 60.seconds, 5.minutes, 15.minutes, 60.minutes)
    }
}
