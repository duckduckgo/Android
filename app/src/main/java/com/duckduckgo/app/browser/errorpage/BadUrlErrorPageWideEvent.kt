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

package com.duckduckgo.app.browser.errorpage

import android.annotation.SuppressLint
import com.duckduckgo.app.browser.customtabs.CustomTabViewModel.Companion.CUSTOM_TAB_NAME_PREFIX
import com.duckduckgo.app.browser.suggestredirect.SuggestRedirectOnUnresolvedErrorFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.FlowStatus.Cancelled
import com.duckduckgo.app.statistics.wideevents.FlowStatus.Failure
import com.duckduckgo.app.statistics.wideevents.FlowStatus.Success
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Monitors the lifecycle of a BAD_URL (unresolved host) error page as a Wide Event flow, measuring
 * whether a redirect suggestion was offered, whether the user took it, and whether the suggested
 * hostname resolved. A successful flow outcome occurs when a redirect suggestion is offered, the
 * user takes it and the new hostname resolves correctly.
 *
 * A repeat BAD_URL error after a refresh keeps the existing flow rather than starting a new one. A
 * repeat BAD_URL after the redirect suggestion was clicked instead finishes the flow as a failure
 * and starts a new one for the new BAD_URL error page.
 */
interface BadUrlErrorPageWideEvent {
    /**
     * Must be invoked when a BAD_URL error page is displayed.
     * @param tabId Indicates the current tab ID.
     */
    fun onBadUrlErrorPageDisplayed(tabId: String)

    /**
     * Must be invoked when a connection error page (device offline during host lookup) is
     * displayed. Unlike other non-BAD_URL error pages, it does not prove the hostname resolved.
     * @param tabId Indicates the current tab ID.
     */
    fun onConnectionErrorPageDisplayed(tabId: String)

    /**
     * Must be invoked when a non-BAD_URL error page that proves the hostname resolved (SSL,
     * malicious site) is displayed.
     * @param tabId Indicates the current tab ID.
     */
    fun onOtherErrorPageDisplayed(tabId: String)

    /**
     * Must be invoked when an OMITTED error (one that does not display an error page) is received.
     * @param tabId Indicates the current tab ID.
     */
    fun onOmittedErrorReceived(tabId: String)

    /**
     * Must be invoked when an error page is refreshed.
     * @param tabId Indicates the current tab ID.
     */
    fun onErrorPageRefreshed(tabId: String)

    /**
     * Must be invoked when a page load settles (reaches full progress) without an error page being displayed.
     * @param tabId Indicates the current tab ID.
     */
    fun onPageLoadFinished(tabId: String)

    /**
     * Must be invoked when a redirect suggestion is displayed on the error page.
     * @param tabId Indicates the current tab ID.
     */
    fun onRedirectSuggested(tabId: String)

    /**
     * Must be invoked when a redirect suggestion is clicked by the user.
     * @param tabId Indicates the current tab ID.
     */
    fun onRedirectClicked(tabId: String)

    /**
     * Must be invoked when the user exits the BAD_URL error page by navigating away.
     * @param tabId Indicates the current tab ID.
     */
    fun onBadUrlErrorPageExited(tabId: String)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBadUrlErrorPageWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val badUrlErrorPageWideEventFeature: Lazy<BadUrlErrorPageWideEventFeature>,
    private val suggestRedirectFeature: SuggestRedirectOnUnresolvedErrorFeature,
    @param:RegularMode private val regularTabRepository: TabRepository,
    @param:FireMode private val fireTabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : BadUrlErrorPageWideEvent {
    // This is to ensure modifications of the wide event are serialized
    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(
        context = appCoroutineScope.coroutineContext +
            dispatchers.computation().limitedParallelism(1),
    )

    private val mutex = Mutex()
    private val activeFlows = ConcurrentHashMap<String, FlowState>()

    // No synchronization needed for this job var as it's only mutated inside a lock
    private var closedTabsObserverJob: Job? = null

    override fun onBadUrlErrorPageDisplayed(tabId: String) {
        coroutineScope.launch {
            mutex.withLock {
                // Custom tabs are excluded: they never register in the tab repositories, so the
                // closed-tabs observer would cancel their flows as abandoned immediately.
                if (tabId.startsWith(CUSTOM_TAB_NAME_PREFIX)) {
                    return@launch
                }
                val existingFlow = activeFlows[tabId]
                if (existingFlow != null) {
                    if (!existingFlow.awaitingRedirectOutcome) {
                        // A refresh that was awaiting its outcome concluded in the same error
                        existingFlow.awaitingRefreshOutcome = false
                        logcat { "Bad URL error page flow already active for tabId=$tabId, keeping it" }
                        return@launch
                    }
                    // The clicked redirect landed on another BAD_URL page: the redirect failed,
                    // and the freshly shown error page starts a flow of its own.
                    finishFlow(tabId, Failure(reason = FAILURE_REASON_NEW_HOSTNAME_RESOLUTION_FAILED))
                }
                // The feature flag only gates starting new flows. Flows already in flight always resolve,
                // here and in every other callback, so no stale state survives a mid-journey flag change.
                if (!isRecordingEnabled()) {
                    return@launch
                }
                startFlow(tabId)
            }
        }
    }

    override fun onConnectionErrorPageDisplayed(tabId: String) {
        coroutineScope.launch {
            mutex.withLock {
                val state = activeFlows[tabId] ?: return@launch
                if (state.awaitingRedirectOutcome) {
                    // The device went offline before the suggested hostname could be looked up. It's neither a success nor a feature failure
                    finishFlow(tabId, Cancelled, mapOf(KEY_CANCEL_REASON to CancelReason.DEVICE_OFFLINE.value))
                } else {
                    finishFlow(tabId, Cancelled, mapOf(KEY_CANCEL_REASON to CancelReason.ERROR_REPLACED_ON_REFRESH.value))
                }
            }
        }
    }

    override fun onOtherErrorPageDisplayed(tabId: String) {
        coroutineScope.launch {
            mutex.withLock {
                val state = activeFlows[tabId] ?: return@launch
                if (state.awaitingRedirectOutcome) {
                    // The suggested hostname resolved. Whatever failed afterwards is out of scope.
                    finishFlow(tabId, Success)
                } else {
                    finishFlow(tabId, Cancelled, mapOf(KEY_CANCEL_REASON to CancelReason.ERROR_REPLACED_ON_REFRESH.value))
                }
            }
        }
    }

    override fun onOmittedErrorReceived(tabId: String) {
        coroutineScope.launch {
            mutex.withLock {
                val state = activeFlows[tabId] ?: return@launch
                state.omittedErrorReceived = true
            }
        }
    }

    override fun onErrorPageRefreshed(tabId: String) {
        coroutineScope.launch {
            mutex.withLock {
                val state = activeFlows[tabId] ?: return@launch
                state.awaitingRefreshOutcome = true
                // Only errors received after the refresh began count towards its outcome
                state.omittedErrorReceived = false
            }
        }
    }

    override fun onPageLoadFinished(tabId: String) {
        coroutineScope.launch {
            mutex.withLock {
                val state = activeFlows[tabId] ?: return@launch
                when {
                    state.awaitingRedirectOutcome -> {
                        // The suggested hostname resolved. Whatever failed afterwards is out of scope.
                        finishFlow(tabId, Success)
                    }
                    state.awaitingRefreshOutcome -> {
                        // Transient errors can be reclassified mid-load, so the load outcome is decided only once the load settles fully.
                        val cancelReason = if (state.omittedErrorReceived) {
                            CancelReason.ERROR_REPLACED_ON_REFRESH
                        } else {
                            CancelReason.RECOVERED_ON_REFRESH
                        }
                        finishFlow(tabId, Cancelled, mapOf(KEY_CANCEL_REASON to cancelReason.value))
                    }
                    else -> Unit // The error page is still displayed, so the flow stays open
                }
            }
        }
    }

    override fun onRedirectSuggested(tabId: String) {
        coroutineScope.launch {
            mutex.withLock {
                val state = activeFlows[tabId] ?: return@launch
                recordStep(state, STEP_REDIRECT_SUGGESTED)
            }
        }
    }

    override fun onRedirectClicked(tabId: String) {
        coroutineScope.launch {
            mutex.withLock {
                val state = activeFlows[tabId] ?: return@launch
                state.awaitingRedirectOutcome = true
                recordStep(state, STEP_REDIRECT_CLICKED)
            }
        }
    }

    override fun onBadUrlErrorPageExited(tabId: String) {
        coroutineScope.launch {
            mutex.withLock {
                finishFlow(tabId, Cancelled, mapOf(KEY_CANCEL_REASON to CancelReason.ABANDONED.value))
            }
        }
    }

    private suspend fun startFlow(tabId: String) {
        wideEventClient.flowStart(
            name = FLOW_NAME,
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
            samplingProbability = 1.0f,
        ).onSuccess { flowId ->
            activeFlows[tabId] = FlowState(flowId)
            startClosedTabsObserverJob()
            logcat { "Bad URL error page flow started: tabId=$tabId, flowId=$flowId" }
            wideEventClient.intervalStart(
                wideEventId = flowId,
                key = KEY_ERROR_PAGE_DURATION,
                buckets = ERROR_PAGE_INTERVAL_BUCKETS,
            )
        }.onFailure { error ->
            logcat { "Failed to start bad URL error page flow for tabId=$tabId: ${error.message}" }
        }
    }

    private suspend fun recordStep(state: FlowState, stepName: String) {
        if (!state.recordedSteps.add(stepName)) {
            logcat { "Ignoring repeat $stepName for flowId=${state.flowId}" }
            return
        }
        wideEventClient.flowStep(
            wideEventId = state.flowId,
            stepName = stepName,
            metadata = mapOf(KEY_LAST_STEP to stepName),
        )
        logcat { "Recorded $stepName for flowId=${state.flowId}" }
    }

    private suspend fun finishFlow(
        tabId: String,
        status: FlowStatus,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val state = activeFlows.remove(tabId) ?: return
        wideEventClient.intervalEnd(
            wideEventId = state.flowId,
            key = KEY_ERROR_PAGE_DURATION,
        )
        wideEventClient.flowFinish(
            wideEventId = state.flowId,
            status = status,
            metadata = metadata,
        )
        logcat { "Bad URL error page flow finished: tabId=$tabId, flowId=${state.flowId}, status=$status, metadata=$metadata" }
        if (activeFlows.isEmpty()) {
            stopClosedTabsObserverJob()
        }
    }

    private fun startClosedTabsObserverJob() {
        if (closedTabsObserverJob != null) {
            return
        }
        closedTabsObserverJob = coroutineScope.launch {
            combine(
                regularTabRepository.flowTabs,
                fireTabRepository.flowTabs,
            ) { regularTabs, fireTabs -> (regularTabs + fireTabs) }
                .map { activeTabs -> activeTabs.map { it.tabId } }
                .distinctUntilChanged()
                .collect { activeTabIds ->
                    mutex.withLock {
                        val inactiveTabIds = activeFlows.keys.filter { it !in activeTabIds }
                        for (tabId in inactiveTabIds) {
                            finishFlow(tabId, Cancelled, mapOf(KEY_CANCEL_REASON to CancelReason.ABANDONED.value))
                        }
                    }
                }
        }
    }

    private fun stopClosedTabsObserverJob() {
        closedTabsObserverJob?.cancel()
        closedTabsObserverJob = null
    }

    private suspend fun isRecordingEnabled(): Boolean = withContext(dispatchers.io()) {
        // Suggest redirect feature conditions to be removed once we remove its feature flag
        badUrlErrorPageWideEventFeature.get().self().isEnabled() &&
            suggestRedirectFeature.self().isEnabled() &&
            suggestRedirectFeature.suggestRedirect().isEnabled()
    }

    private class FlowState(val flowId: Long) {
        val recordedSteps: MutableSet<String> = mutableSetOf()
        var awaitingRedirectOutcome: Boolean = false
        var awaitingRefreshOutcome: Boolean = false
        var omittedErrorReceived: Boolean = false
    }

    private enum class CancelReason(val value: String) {
        RECOVERED_ON_REFRESH("recovered_on_refresh"),
        ABANDONED("abandoned"),
        ERROR_REPLACED_ON_REFRESH("error_replaced_on_refresh"),
        DEVICE_OFFLINE("device_offline"),
    }

    private companion object {
        val ERROR_PAGE_INTERVAL_BUCKETS: Set<Duration> = setOf(
            1.seconds,
            2.seconds,
            3.seconds,
            4.seconds,
            5.seconds,
            10.seconds,
            30.seconds,
            1.minutes,
        )
        const val FLOW_NAME = "bad-url-error-page"
        const val STEP_REDIRECT_SUGGESTED = "redirect_suggested"
        const val STEP_REDIRECT_CLICKED = "redirect_clicked"
        const val KEY_CANCEL_REASON = "cancel_reason"
        const val KEY_LAST_STEP = "last_step"
        const val KEY_ERROR_PAGE_DURATION = "error_page_duration_ms_bucketed"
        const val FAILURE_REASON_NEW_HOSTNAME_RESOLUTION_FAILED = "new_hostname_resolution_failed"
    }
}
