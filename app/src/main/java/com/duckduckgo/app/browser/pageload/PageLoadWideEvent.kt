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

package com.duckduckgo.app.browser.pageload

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.pixels.remoteconfig.OptimizeTrackerEvaluationRCWrapper
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Tracks page load events as Wide Event flows with multi-phase tracking.
 * Manages flow lifecycle: page_start → page_visible → page_escaped_fixed_progress → page_finish
 */
interface PageLoadWideEvent {
    /**
     * Called when a page starts loading.
     * Creates a new Wide Event flow and records page_start step.
     *
     * @param tabId The unique identifier for the tab loading the page
     */
    suspend fun startPageLoad(tabId: String)

    /**
     * Called when page becomes visible.
     * Records page_visible flow step with elapsed time and current progress.
     *
     * @param tabId The unique identifier for the tab
     * @param currentProgress The WebView progress (0-100)
     */
    suspend fun recordPageVisible(tabId: String, currentProgress: Int)

    /**
     * Called when progress escapes fixed progress zone.
     * Records page_escaped_fixed_progress flow step with elapsed time and actual progress.
     *
     * @param tabId The unique identifier for the tab
     * @param actualProgress The progress when escaping fixed zone (typically >= 50)
     */
    suspend fun recordExitedFixedProgress(tabId: String, actualProgress: Int)

    /**
     * Called from BrowserWebViewClient.onPageFinished or onReceivedError when page load completes.
     * Records page_finish flow step and completes the Wide Event flow.
     *
     * @param tabId The unique identifier for the tab
     * @param outcome "success", "error", or "timeout"
     * @param errorCode Optional error code for failures
     * @param isTabInForegroundOnFinish Whether the tab was in foreground when page finished loading
     * @param activeRequestsOnLoadStart Number of active network requests when page load started
     * @param concurrentRequestsOnFinish Number of concurrent network requests when page finished loading
     */
    suspend fun finishPageLoad(
        tabId: String,
        outcome: String,
        errorCode: String?,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    )
}

@ContributesBinding(AppScope::class)
class RealPageLoadWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val deviceInfo: DeviceInfo,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val autoconsent: Autoconsent,
    private val optimizeTrackerEvaluationRCWrapper: OptimizeTrackerEvaluationRCWrapper,
    private val androidBrowserConfigFeature: Lazy<AndroidBrowserConfigFeature>,
    private val dispatchers: DispatcherProvider,
) : PageLoadWideEvent {
    private val activeFlows = ConcurrentHashMap<String, Long>()

    override suspend fun startPageLoad(tabId: String) {
        if (!isFeatureEnabled()) return

        val result = wideEventClient.flowStart(
            name = PAGE_LOAD_FEATURE_NAME,
            cleanupPolicy = CleanupPolicy.OnTimeout(5.minutes.toJavaDuration()),
        )

        result.onSuccess { flowId ->
            activeFlows[tabId] = flowId
            logcat { "Page load flow started: tabId=$tabId, flowId=$flowId" }

            wideEventClient.flowStep(
                wideEventId = flowId,
                stepName = STEP_PAGE_START,
            )

            wideEventClient.intervalStart(
                wideEventId = flowId,
                key = KEY_ELAPSED_TIME_TO_FINISH,
            )

            wideEventClient.intervalStart(
                wideEventId = flowId,
                key = KEY_ELAPSED_TIME_TO_VISIBLE,
            )

            wideEventClient.intervalStart(
                wideEventId = flowId,
                key = KEY_ELAPSED_TIME_TO_ESCAPED_FIXED_PROGRESS,
            )
        }.onFailure { error ->
            logcat { "Failed to start page load flow for tabId=$tabId: ${error.message}" }
        }
    }

    override suspend fun recordPageVisible(tabId: String, currentProgress: Int) {
        if (!isFeatureEnabled()) return
        val flowId = getActiveFlowId(tabId) ?: return

        wideEventClient.intervalEnd(
            wideEventId = flowId,
            key = KEY_ELAPSED_TIME_TO_VISIBLE,
        )

        wideEventClient.flowStep(
            wideEventId = flowId,
            stepName = STEP_PAGE_VISIBLE,
            metadata = mapOf(
                KEY_PROGRESS to currentProgress.toString(),
            ),
        )

        logcat { "Page visible recorded: tabId=$tabId, flowId=$flowId, progress=$currentProgress" }
    }

    override suspend fun recordExitedFixedProgress(tabId: String, actualProgress: Int) {
        if (!isFeatureEnabled()) return

        val flowId = getActiveFlowId(tabId) ?: return

        wideEventClient.intervalEnd(
            wideEventId = flowId,
            key = KEY_ELAPSED_TIME_TO_ESCAPED_FIXED_PROGRESS,
        )

        wideEventClient.flowStep(
            wideEventId = flowId,
            stepName = STEP_PAGE_ESCAPED_FIXED_PROGRESS,
            metadata = mapOf(
                KEY_PROGRESS to actualProgress.toString(),
            ),
        )

        logcat { "Exited fixed progress: tabId=$tabId, flowId=$flowId, actualProgress=$actualProgress" }
    }

    override suspend fun finishPageLoad(
        tabId: String,
        outcome: String,
        errorCode: String?,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    ) {
        if (!isFeatureEnabled()) return

        val flowId = popActiveFlowId(tabId) ?: return

        wideEventClient.intervalEnd(
            wideEventId = flowId,
            key = KEY_ELAPSED_TIME_TO_FINISH,
        )

        wideEventClient.flowStep(
            wideEventId = flowId,
            stepName = STEP_PAGE_FINISH,
            success = (outcome == "success"),
            metadata = mutableMapOf<String, String>().apply {
                put(KEY_OUTCOME, outcome)
                errorCode?.let { put(KEY_ERROR_CODE, it) }
            },
        )

        val flowStatus = if (outcome == "success") {
            FlowStatus.Success
        } else {
            FlowStatus.Failure(outcome)
        }
        wideEventClient.flowFinish(
            wideEventId = flowId,
            status = flowStatus,
            metadata = mapOf(
                KEY_APP_VERSION to deviceInfo.appVersion,
                KEY_WEBVIEW_VERSION to webViewVersionProvider.getMajorVersion(),
                KEY_CPM_ENABLED to autoconsent.isAutoconsentEnabled().toString(),
                KEY_TRACKER_OPTIMIZATION_ENABLED to optimizeTrackerEvaluationRCWrapper.enabled.toString(),
                KEY_IS_TAB_IN_FOREGROUND_ON_FINISH to isTabInForegroundOnFinish.toString(),
                KEY_ACTIVE_REQUESTS_ON_LOAD_START to activeRequestsOnLoadStart.toString(),
                KEY_CONCURRENT_REQUESTS_ON_FINISH to concurrentRequestsOnFinish.toString(),
            ),
        )

        logcat { "Page load finished: tabId=$tabId, flowId=$flowId, outcome=$outcome, errorCode=$errorCode" }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        androidBrowserConfigFeature.get().sendPageLoadWideEvent().isEnabled()
    }

    private fun getActiveFlowId(tabId: String): Long? {
        val flowId = activeFlows[tabId]
        if (flowId == null) {
            logcat { "No active flow found for tabId=$tabId" }
        }
        return flowId
    }

    private fun popActiveFlowId(tabId: String): Long? {
        val flowId = activeFlows.remove(tabId)
        if (flowId == null) {
            logcat { "No active flow found to pop for tabId=$tabId" }
        }
        return flowId
    }

    private companion object {
        const val PAGE_LOAD_FEATURE_NAME = "page-load"
        const val STEP_PAGE_START = "page_start"
        const val STEP_PAGE_VISIBLE = "page_visible"
        const val STEP_PAGE_ESCAPED_FIXED_PROGRESS = "page_escaped_fixed_progress"
        const val STEP_PAGE_FINISH = "page_finish"
        const val KEY_ELAPSED_TIME_TO_FINISH = "elapsed_time_to_finish"
        const val KEY_ELAPSED_TIME_TO_VISIBLE = "elapsed_time_to_visible"
        const val KEY_ELAPSED_TIME_TO_ESCAPED_FIXED_PROGRESS = "elapsed_time_to_escaped_fixed_progress"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTCOME = "outcome"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_APP_VERSION = "app_version_when_page_loaded"
        const val KEY_WEBVIEW_VERSION = "webview_version"
        const val KEY_CPM_ENABLED = "cpm_enabled"
        const val KEY_TRACKER_OPTIMIZATION_ENABLED = "tracker_optimization_enabled_v2"
        const val KEY_IS_TAB_IN_FOREGROUND_ON_FINISH = "is_tab_in_foreground_on_finish"
        const val KEY_ACTIVE_REQUESTS_ON_LOAD_START = "active_requests_on_load_start"
        const val KEY_CONCURRENT_REQUESTS_ON_FINISH = "concurrent_requests_on_finish"
    }
}
