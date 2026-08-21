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

package com.duckduckgo.app.browser.returnsession

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.generalsettings.showonapplaunch.FirstScreenHandlerImpl
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.browser.api.wideevents.BrowserInteractionsPlugin
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.interactions.HatchInteractionsPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Covers every return to the app.
 *
 * Session start waits for [FirstScreenHandlerImpl] to finish applying the opening-screen decision.
 * This makes `after_idle` and `landed_on` authoritative at [WideEventClient.flowStart].
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ReturnSessionLandingListener::class)
@ContributesMultibinding(AppScope::class, boundType = BrowserInteractionsPlugin::class)
@ContributesMultibinding(AppScope::class, boundType = HatchInteractionsPlugin::class)
class RealReturnSessionWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val settingsDataStore: SettingsDataStore,
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val returnSessionWideEventFeature: Lazy<ReturnSessionWideEventFeature>,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : BrowserInteractionsPlugin, HatchInteractionsPlugin, ReturnSessionLandingListener {

    // Serializes wide-event mutations.
    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(
        context = appCoroutineScope.coroutineContext +
            dispatchers.computation().limitedParallelism(1),
    )

    private val mutex = Mutex()
    private var activeSession: SessionState? = null
    private var pendingLandingFocus: Boolean? = null
    private var pendingEngagement: Boolean = false

    init {
        showOnAppLaunchOptionDataStore.optionFlow
            .distinctUntilChangedBy { option -> if (option is SpecificPage) option.url else option }
            .drop(1)
            .onEach {
                recordNonTerminal(
                    action = "opening_screen_changed",
                    isAlreadyRecorded = { it.openingScreenChanged },
                ) { it.openingScreenChanged = true }
            }
            .launchIn(coroutineScope)
    }

    override fun onReturnLandingResolved(result: ReturnSessionLandingResult) {
        coroutineScope.launch {
            mutex.withLock {
                if (abortIfDisabled()) return@launch

                activeSession?.let { prior ->
                    logcat(tag = TAG) { "Aborting prior return session on new foreground" }
                    wideEventClient.flowAbort(prior.flowId)
                    activeSession = null
                }

                val landingFocus = pendingLandingFocus
                pendingLandingFocus = null
                val landingEngagement = pendingEngagement
                pendingEngagement = false
                val startResult = wideEventClient.flowStart(
                    name = FEATURE_NAME,
                    cleanupPolicy = CleanupPolicy.OnProcessStart(
                        ignoreIfIntervalTimeoutPresent = false,
                        flowStatus = FlowStatus.Unknown,
                    ),
                    metadata = buildMap {
                        put(KEY_AFTER_IDLE, result.afterIdle.toString())
                        put(KEY_LANDED_ON, result.landing.value)
                        timeAwayMsBucketed()?.let { put(KEY_TIME_AWAY, it) }
                        // Default in case the process is killed before a real terminal reason lands.
                        // OnProcessStart cleanup sends whatever metadata is already persisted.
                        put(KEY_STATUS_REASON, REASON_APP_TERMINATED)
                    },
                    samplingProbability = SAMPLING_PROBABILITY,
                )

                startResult.onSuccess { flowId ->
                    val session = SessionState(
                        flowId = flowId,
                        afterIdle = result.afterIdle,
                        landedOn = result.landing,
                        focused = landingFocus,
                        pageEngaged = landingEngagement,
                    )
                    activeSession = session
                    logcat(tag = TAG) { "Return session started: landedOn=${result.landing.value}" }

                    wideEventClient.intervalStart(flowId, KEY_SESSION_DURATION, buckets = DURATION_BUCKETS)
                    wideEventClient.intervalStart(flowId, KEY_TIME_TO_FIRST_INTERACTION, buckets = DURATION_BUCKETS)
                    if (landingEngagement) {
                        wideEventClient.intervalEnd(flowId, KEY_TIME_TO_FIRST_INTERACTION)
                        session.firstInteractionRecorded = true
                    }
                }.onFailure { error ->
                    logcat(tag = TAG) { "Failed to start return session: ${error.message}" }
                }
            }
        }
    }

    override fun onLandingFocusCaptured(focused: Boolean) {
        coroutineScope.launch {
            mutex.withLock {
                if (abortIfDisabled()) return@launch
                val session = activeSession
                if (session == null) {
                    if (pendingLandingFocus == null) pendingLandingFocus = focused
                } else if (session.focused == null) {
                    session.focused = focused
                }
            }
        }
    }

    override fun onReturnClosed() {
        coroutineScope.launch {
            mutex.withLock {
                if (abortIfDisabled()) return@launch
                if (activeSession == null) {
                    pendingLandingFocus = null
                    pendingEngagement = false
                }
                finishSessionLocked(
                    statusReason = REASON_APP_BACKGROUNDED,
                    status = FlowStatus.Cancelled,
                )
            }
        }
    }

    private fun timeAwayMsBucketed(): String? {
        val lastBackgrounded = settingsDataStore.lastSessionBackgroundTimestamp
        if (lastBackgrounded == 0L) return null
        val elapsedMs = System.currentTimeMillis() - lastBackgrounded
        return when {
            elapsedMs < 60_000L -> "0"
            elapsedMs < 300_000L -> "60000"
            elapsedMs < 900_000L -> "300000"
            elapsedMs < 1_800_000L -> "900000"
            elapsedMs < 3_600_000L -> "1800000"
            else -> "3600000"
        }
    }

    override fun onSearchSubmitted() {
        finishSession(statusReason = REASON_SEARCH_SUBMITTED, status = FlowStatus.Success)
    }

    override fun onUrlSubmitted() {
        finishSession(statusReason = REASON_URL_SUBMITTED, status = FlowStatus.Success)
    }

    override fun onAiPromptSubmitted() {
        finishSession(statusReason = REASON_AI_PROMPT_SUBMITTED, status = FlowStatus.Success)
    }

    override fun onChatSelected() {
        finishSession(statusReason = REASON_CHAT_SELECTED, status = FlowStatus.Success)
    }

    override fun onReturnToPageTapped() {
        finishSession(statusReason = REASON_RETURN_TO_PAGE_TAPPED, status = FlowStatus.Success)
    }

    override fun onTabSwitcherSelected() {
        finishSession(statusReason = REASON_TAB_SWITCHER_SELECTED, status = FlowStatus.Success)
    }

    override fun onFavoriteSelected() {
        finishSession(statusReason = REASON_FAVORITE_SELECTED, status = FlowStatus.Success)
    }

    override fun onBackPressed() {
        recordNonTerminal(action = "back_pressed", isAlreadyRecorded = { it.backPressed }) { it.backPressed = true }
    }

    override fun onWebViewEngaged() {
        recordNonTerminal(action = "page_engaged", isAlreadyRecorded = { it.pageEngaged }) { it.pageEngaged = true }
    }

    override fun onNtpEngaged() {
        coroutineScope.launch {
            mutex.withLock {
                if (abortIfDisabled()) return@launch
                val session = activeSession
                if (session == null) {
                    pendingEngagement = true
                    return@launch
                }
                if (session.pageEngaged) return@launch

                session.pageEngaged = true
                if (!session.firstInteractionRecorded) {
                    wideEventClient.intervalEnd(session.flowId, KEY_TIME_TO_FIRST_INTERACTION)
                    session.firstInteractionRecorded = true
                }
                logcat(tag = TAG) { "Return session: page_engaged recorded" }
            }
        }
    }

    override fun onCloseTabTapped() {
        recordNonTerminal(action = "close_tab_tapped", isAlreadyRecorded = { it.closeTabTapped }) { it.closeTabTapped = true }
    }

    override fun onBurnTabTapped() {
        recordNonTerminal(action = "burn_tab_tapped", isAlreadyRecorded = { it.burnTabTapped }) { it.burnTabTapped = true }
    }

    private fun recordNonTerminal(
        action: String,
        isAlreadyRecorded: (SessionState) -> Boolean,
        updateState: (SessionState) -> Unit,
    ) {
        coroutineScope.launch {
            mutex.withLock {
                if (abortIfDisabled()) return@launch
                val session = activeSession ?: return@launch
                if (isAlreadyRecorded(session)) return@launch

                updateState(session)
                if (!session.firstInteractionRecorded) {
                    wideEventClient.intervalEnd(session.flowId, KEY_TIME_TO_FIRST_INTERACTION)
                    session.firstInteractionRecorded = true
                }
                logcat(tag = TAG) { "Return session: $action recorded" }
            }
        }
    }

    private fun finishSession(statusReason: String, status: FlowStatus) {
        coroutineScope.launch {
            mutex.withLock {
                if (abortIfDisabled()) return@launch
                finishSessionLocked(statusReason, status)
            }
        }
    }

    private suspend fun finishSessionLocked(
        statusReason: String,
        status: FlowStatus,
    ) {
        val session = activeSession ?: return
        activeSession = null

        wideEventClient.intervalEnd(session.flowId, KEY_SESSION_DURATION)
        if (status == FlowStatus.Success && !session.firstInteractionRecorded) {
            wideEventClient.intervalEnd(session.flowId, KEY_TIME_TO_FIRST_INTERACTION)
        }
        wideEventClient.flowFinish(
            wideEventId = session.flowId,
            status = status,
            metadata = mapOf(
                KEY_AFTER_IDLE to session.afterIdle.toString(),
                KEY_LANDED_ON to session.landedOn.value,
                KEY_STATUS_REASON to statusReason,
                KEY_FOCUSED to (session.focused ?: false).toString(),
                KEY_PAGE_ENGAGED to session.pageEngaged.toString(),
                KEY_BACK_PRESSED to session.backPressed.toString(),
                KEY_OPENING_SCREEN_CHANGED to session.openingScreenChanged.toString(),
                KEY_CLOSE_TAB_TAPPED to session.closeTabTapped.toString(),
                KEY_BURN_TAB_TAPPED to session.burnTabTapped.toString(),
            ),
        )
        logcat(tag = TAG) { "Return session finished: status=$status, reason=$statusReason" }
    }

    /**
     * Must be called under [mutex]. When the feature is disabled, aborts any active session instead
     * of just skipping — otherwise a session started while enabled would linger persisted and get
     * force-completed as Unknown by process-start cleanup after the kill switch flipped.
     */
    private suspend fun abortIfDisabled(): Boolean {
        if (isFeatureEnabled()) return false
        pendingLandingFocus = null
        pendingEngagement = false
        activeSession?.let { session ->
            wideEventClient.flowAbort(session.flowId)
            activeSession = null
        }
        return true
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        returnSessionWideEventFeature.get().self().isEnabled()
    }

    private class SessionState(
        val flowId: Long,
        val afterIdle: Boolean,
        val landedOn: ReturnSessionLanding,
        var focused: Boolean? = null,
        var pageEngaged: Boolean = false,
        var backPressed: Boolean = false,
        var openingScreenChanged: Boolean = false,
        var closeTabTapped: Boolean = false,
        var burnTabTapped: Boolean = false,
        var firstInteractionRecorded: Boolean = false,
    )

    private companion object {
        const val TAG = "RealReturnSessionWideEvent"
        const val FEATURE_NAME = "return_session"
        const val SAMPLING_PROBABILITY = 0.05f

        const val REASON_SEARCH_SUBMITTED = "search_submitted"
        const val REASON_URL_SUBMITTED = "url_submitted"
        const val REASON_AI_PROMPT_SUBMITTED = "ai_prompt_submitted"
        const val REASON_CHAT_SELECTED = "chat_selected"
        const val REASON_RETURN_TO_PAGE_TAPPED = "return_to_page_tapped"
        const val REASON_TAB_SWITCHER_SELECTED = "tab_switcher_selected"
        const val REASON_FAVORITE_SELECTED = "favorite_selected"
        const val REASON_APP_BACKGROUNDED = "app_backgrounded"
        const val REASON_APP_TERMINATED = "app_terminated"

        const val KEY_AFTER_IDLE = "after_idle"
        const val KEY_LANDED_ON = "landed_on"
        const val KEY_TIME_AWAY = "time_away_ms_bucketed"
        const val KEY_STATUS_REASON = "status_reason"
        const val KEY_FOCUSED = "focused"
        const val KEY_PAGE_ENGAGED = "page_engaged"
        const val KEY_BACK_PRESSED = "back_pressed"
        const val KEY_OPENING_SCREEN_CHANGED = "opening_screen_changed"
        const val KEY_CLOSE_TAB_TAPPED = "close_tab_tapped"
        const val KEY_BURN_TAB_TAPPED = "burn_tab_tapped"
        const val KEY_SESSION_DURATION = "session_duration_ms_bucketed"
        const val KEY_TIME_TO_FIRST_INTERACTION = "time_to_first_interaction_ms_bucketed"

        val DURATION_BUCKETS: Set<Duration> = setOf(
            1.seconds,
            5.seconds,
            10.seconds,
            30.seconds,
            60.seconds,
            300.seconds,
            600.seconds,
        )
    }
}
