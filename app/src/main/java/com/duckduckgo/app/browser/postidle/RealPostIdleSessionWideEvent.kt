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

package com.duckduckgo.app.browser.postidle

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.browser.api.wideevents.BrowserInteractionsPlugin
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.newtabpage.api.interactions.HatchInteractionsPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class, boundType = BrowserLifecycleObserver::class)
@ContributesMultibinding(AppScope::class, boundType = BrowserInteractionsPlugin::class)
@ContributesMultibinding(AppScope::class, boundType = HatchInteractionsPlugin::class)
class RealPostIdleSessionWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val duckChatInputModeState: DuckChatInputModeState,
    private val androidBrowserConfigFeature: Lazy<AndroidBrowserConfigFeature>,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : BrowserInteractionsPlugin, HatchInteractionsPlugin, BrowserLifecycleObserver {

    private enum class Surface(val value: String) {
        NTP("ntp"),
        LUT("lut"),
    }

    // Serializes wide-event mutations.
    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(
        context = appCoroutineScope.coroutineContext +
            dispatchers.computation().limitedParallelism(1),
    )

    private val mutex = Mutex()
    private var activeSession: SessionState? = null

    init {
        // drop(1) skips the StateFlow's replayed snapshot — only later transitions are toggles.
        duckChatInputModeState.displayedMode
            .drop(1)
            .onEach { onToggleUsedInternal() }
            .launchIn(coroutineScope)
    }

    private fun onSurfaceShown(surface: Surface) {
        coroutineScope.launch {
            mutex.withLock {
                if (!isFeatureEnabled()) return@launch

                activeSession?.let { prior ->
                    logcat(tag = TAG) { "Aborting prior post-idle session ${prior.flowId} on new surface ${surface.value}" }
                    wideEventClient.flowAbort(prior.flowId)
                    activeSession = null
                }

                val result = wideEventClient.flowStart(
                    name = FEATURE_NAME,
                    cleanupPolicy = CleanupPolicy.OnProcessStart(
                        ignoreIfIntervalTimeoutPresent = false,
                        flowStatus = FlowStatus.Unknown,
                    ),
                    metadata = mapOf(KEY_SURFACE to surface.value),
                )

                result.onSuccess { flowId ->
                    activeSession = SessionState(flowId = flowId, surface = surface)
                    logcat(tag = TAG) { "Post-idle session started: flowId=$flowId, surface=${surface.value}" }

                    wideEventClient.flowStep(flowId, STEP_SURFACE_SHOWN)
                    wideEventClient.intervalStart(flowId, KEY_SESSION_DURATION)
                    wideEventClient.intervalStart(flowId, KEY_TIME_TO_FIRST_INTERACTION)
                }.onFailure { error ->
                    logcat(tag = TAG) { "Failed to start post-idle session: ${error.message}" }
                }
            }
        }
    }

    override fun onLutShownAfterIdle() = onSurfaceShown(Surface.LUT)

    override fun onHatchShownAfterIdle() = onSurfaceShown(Surface.NTP)

    override fun onWebViewEngaged() = onPageEngaged()

    override fun onNtpEngaged() = onPageEngaged()

    override fun onBackPressed() {
        recordNonTerminal(STEP_BACK_PRESSED, isAlreadyRecorded = { it.backPressed }) { it.backPressed = true }
    }

    override fun onBarUsed() {
        finishSession(STEP_BAR_USED, statusReason = STEP_BAR_USED, status = FlowStatus.Success)
    }

    override fun onReturnToPageTapped() {
        finishSession(STEP_RETURN_TO_PAGE_TAPPED, statusReason = STEP_RETURN_TO_PAGE_TAPPED, status = FlowStatus.Success)
    }

    override fun onTabSwitcherSelected() {
        finishSession(STEP_TAB_SWITCHER_SELECTED, statusReason = STEP_TAB_SWITCHER_SELECTED, status = FlowStatus.Success)
    }

    override fun onFavoriteSelected() {
        finishSession(STEP_FAVORITE_SELECTED, statusReason = STEP_FAVORITE_SELECTED, status = FlowStatus.Success)
    }

    private fun onPageEngaged() {
        recordNonTerminal(STEP_PAGE_ENGAGED, isAlreadyRecorded = { it.pageEngaged }) { it.pageEngaged = true }
    }

    override fun onOpen(isFreshLaunch: Boolean) {
        // Sessions start via onSurfaceShown, not lifecycle.
    }

    override fun onClose() {
        finishSession(STEP_APP_BACKGROUNDED, statusReason = STEP_APP_BACKGROUNDED, status = FlowStatus.Cancelled)
    }

    private fun onToggleUsedInternal() {
        recordNonTerminal(STEP_TOGGLE_USED, isAlreadyRecorded = { it.toggleUsed }) { it.toggleUsed = true }
    }

    private fun recordNonTerminal(
        stepName: String,
        isAlreadyRecorded: (SessionState) -> Boolean,
        updateState: (SessionState) -> Unit,
    ) {
        coroutineScope.launch {
            mutex.withLock {
                if (!isFeatureEnabled()) return@launch
                val session = activeSession ?: return@launch
                if (isAlreadyRecorded(session)) return@launch

                wideEventClient.flowStep(session.flowId, stepName)
                updateState(session)
                if (!session.firstInteractionRecorded) {
                    wideEventClient.intervalEnd(session.flowId, KEY_TIME_TO_FIRST_INTERACTION)
                    session.firstInteractionRecorded = true
                }
                logcat(tag = TAG) { "Post-idle session ${session.flowId}: step=$stepName recorded" }
            }
        }
    }

    private fun finishSession(stepName: String, statusReason: String, status: FlowStatus) {
        coroutineScope.launch {
            mutex.withLock {
                if (!isFeatureEnabled()) return@launch
                val session = activeSession ?: return@launch
                activeSession = null

                wideEventClient.flowStep(session.flowId, stepName, success = status is FlowStatus.Success)
                wideEventClient.intervalEnd(session.flowId, KEY_SESSION_DURATION)
                if (!session.firstInteractionRecorded) {
                    wideEventClient.intervalEnd(session.flowId, KEY_TIME_TO_FIRST_INTERACTION)
                }
                wideEventClient.flowFinish(
                    wideEventId = session.flowId,
                    status = status,
                    metadata = mapOf(
                        KEY_SURFACE to session.surface.value,
                        KEY_STATUS_REASON to statusReason,
                        KEY_PAGE_ENGAGED to session.pageEngaged.toString(),
                        KEY_TOGGLE_USED to session.toggleUsed.toString(),
                        KEY_BACK_PRESSED to session.backPressed.toString(),
                    ),
                )
                logcat(tag = TAG) { "Post-idle session ${session.flowId} finished: status=$status, reason=$statusReason" }
            }
        }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        androidBrowserConfigFeature.get().sendPostIdleSessionWideEvent().isEnabled()
    }

    private class SessionState(
        val flowId: Long,
        val surface: Surface,
        var pageEngaged: Boolean = false,
        var toggleUsed: Boolean = false,
        var backPressed: Boolean = false,
        var firstInteractionRecorded: Boolean = false,
    )

    private companion object {
        const val TAG = "RealPostIdleSessionWideEvent"
        const val FEATURE_NAME = "post_idle_session"

        const val STEP_SURFACE_SHOWN = "surface_shown"
        const val STEP_PAGE_ENGAGED = "page_engaged"
        const val STEP_TOGGLE_USED = "toggle_used"
        const val STEP_BACK_PRESSED = "back_pressed"
        const val STEP_BAR_USED = "bar_used"
        const val STEP_RETURN_TO_PAGE_TAPPED = "return_to_page_tapped"
        const val STEP_TAB_SWITCHER_SELECTED = "tab_switcher_selected"
        const val STEP_FAVORITE_SELECTED = "favorite_selected"
        const val STEP_APP_BACKGROUNDED = "app_backgrounded"

        const val KEY_SURFACE = "surface"
        const val KEY_STATUS_REASON = "status_reason"
        const val KEY_PAGE_ENGAGED = "page_engaged"
        const val KEY_TOGGLE_USED = "toggle_used"
        const val KEY_BACK_PRESSED = "back_pressed"
        const val KEY_SESSION_DURATION = "session_duration_ms_bucketed"
        const val KEY_TIME_TO_FIRST_INTERACTION = "time_to_first_interaction_ms_bucketed"
    }
}
