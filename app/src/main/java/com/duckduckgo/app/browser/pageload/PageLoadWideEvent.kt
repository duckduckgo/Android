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

import android.annotation.SuppressLint
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedSites
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.pixels.remoteconfig.OptimizeTrackerEvaluationRCWrapper
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * @param tabId The unique identifier for the tab
     * @param url The URL of the page being loaded
     */
    fun onPageStarted(tabId: String, url: String)

    /**
     * Called when a page becomes visible to the user.
     * @param tabId The unique identifier for the tab
     * @param url The URL of the page being loaded
     * @param progress The current page load progress (0-100)
     */
    fun onPageVisible(tabId: String, url: String, progress: Int)

    /**
     * Called when page progress changes and escapes the fixed progress state.
     * @param tabId The unique identifier for the tab
     * @param url The URL of the page being loaded
     */
    fun onProgressChanged(tabId: String, url: String)

    /**
     * Called when a page finishes loading (either successfully or with an error).
     * @param tabId The unique identifier for the tab
     * @param url The URL of the page that finished loading
     * @param errorDescription Optional error description. If null, indicates successful load.
     * @param isTabInForegroundOnFinish Whether the tab was in the foreground when finished
     * @param activeRequestsOnLoadStart Number of parallel requests when page load started
     * @param concurrentRequestsOnFinish Number of concurrent requests when page finished
     */
    fun onPageLoadFinished(
        tabId: String,
        url: String,
        errorDescription: String? = null,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    )
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealPageLoadWideEvent @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val autoconsent: Autoconsent,
    private val optimizeTrackerEvaluationRCWrapper: OptimizeTrackerEvaluationRCWrapper,
    private val androidBrowserConfigFeature: Lazy<AndroidBrowserConfigFeature>,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : PageLoadWideEvent {

    // This is to ensure modifications of the wide event are serialized
    @SuppressLint("AvoidComputationUsage")
    private val coroutineScope = CoroutineScope(
        context = appCoroutineScope.coroutineContext +
            dispatchers.computation().limitedParallelism(1),
    )

    private val mutex = Mutex()
    private val activeFlows = ConcurrentHashMap<String, PageLoadState>()

    override fun onPageStarted(tabId: String, url: String) {
        if (!shouldTrackUrl(url)) return
        if (isInProgress(tabId, url)) return
        coroutineScope.launch {
            mutex.withLock {
                if (!isFeatureEnabled()) return@launch

                val existingState = activeFlows[tabId]
                if (existingState != null && existingState.url != url) {
                    logcat { "Cancelling previous flow for tabId=$tabId (${existingState.url} → $url)" }
                    activeFlows.remove(tabId)
                    wideEventClient.flowAbort(existingState.flowId)
                }

                val result = wideEventClient.flowStart(
                    name = PAGE_LOAD_FEATURE_NAME,
                    cleanupPolicy = CleanupPolicy.OnTimeout(CLEANUP_TIMEOUT.toJavaDuration()),
                )

                result.onSuccess { flowId ->
                    activeFlows[tabId] = PageLoadState(flowId, url)
                    logcat { "Page load flow started: tabId=$tabId, url=$url, flowId=$flowId" }

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
        }
    }

    override fun onPageVisible(tabId: String, url: String, progress: Int) {
        if (!isInProgress(tabId, url)) return
        updateWideEventAsync(tabId) { flowId ->
            wideEventClient.intervalEnd(
                wideEventId = flowId,
                key = KEY_ELAPSED_TIME_TO_VISIBLE,
            )

            wideEventClient.flowStep(
                wideEventId = flowId,
                stepName = STEP_PAGE_VISIBLE,
                metadata = mapOf(
                    KEY_PROGRESS to (progress >= FIXED_PROGRESS_THRESHOLD).toString(),
                ),
            )

            logcat { "Page visible recorded: flowId=$flowId, progress=$progress" }
        }
    }

    override fun onProgressChanged(tabId: String, url: String) {
        if (!isInProgress(tabId, url)) return
        updateWideEventAsync(tabId) { flowId ->
            wideEventClient.intervalEnd(
                wideEventId = flowId,
                key = KEY_ELAPSED_TIME_TO_ESCAPED_FIXED_PROGRESS,
            )
            wideEventClient.flowStep(
                wideEventId = flowId,
                stepName = STEP_PAGE_ESCAPED_FIXED_PROGRESS,
            )
            logcat { "Exited fixed progress: flowId=$flowId" }
        }
    }

    override fun onPageLoadFinished(
        tabId: String,
        url: String,
        errorDescription: String?,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    ) {
        if (!isInProgress(tabId, url)) return
        val isSuccess = errorDescription == null
        val outcome = if (isSuccess) "success" else "error"
        updateWideEventAsync(tabId) { flowId ->
            popActiveFlowId(tabId)

            wideEventClient.intervalEnd(
                wideEventId = flowId,
                key = KEY_ELAPSED_TIME_TO_FINISH,
            )

            wideEventClient.flowStep(
                wideEventId = flowId,
                stepName = STEP_PAGE_FINISH,
                success = isSuccess,
                metadata = mutableMapOf<String, String>().apply {
                    put(KEY_OUTCOME, outcome)
                    errorDescription?.let { put(KEY_ERROR_CODE, it) }
                },
            )

            val flowStatus = if (isSuccess) {
                FlowStatus.Success
            } else {
                FlowStatus.Failure(errorDescription ?: "")
            }
            wideEventClient.flowFinish(
                wideEventId = flowId,
                status = flowStatus,
                metadata = mapOf(
                    KEY_WEBVIEW_VERSION to webViewVersionProvider.getMajorVersion(),
                    KEY_CPM_ENABLED to autoconsent.isAutoconsentEnabled().toString(),
                    KEY_TRACKER_OPTIMIZATION_ENABLED to optimizeTrackerEvaluationRCWrapper.enabled.toString(),
                    KEY_IS_TAB_IN_FOREGROUND_ON_FINISH to isTabInForegroundOnFinish.toString(),
                    KEY_ACTIVE_REQUESTS_ON_LOAD_START to activeRequestsOnLoadStart.toString(),
                    KEY_CONCURRENT_REQUESTS_ON_FINISH to concurrentRequestsOnFinish.toString(),
                ),
            )

            logcat { "Page load finished: tabId=$tabId, flowId=$flowId, outcome=$outcome" }
        }
    }

    private fun isInProgress(tabId: String, url: String): Boolean {
        val state = activeFlows[tabId] ?: return false
        val ageMillis = currentTimeProvider.currentTimeMillis() - state.createdAt
        val isStale = ageMillis > CLEANUP_TIMEOUT.inWholeMilliseconds
        return state.url == url && !isStale
    }

    private fun shouldTrackUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (url == ABOUT_BLANK) return false
        return runCatching {
            PageLoadedSites.perfSites.any { site -> UriString.sameOrSubdomain(url, site) }
        }.getOrDefault(false)
    }

    private fun updateWideEventAsync(tabId: String, operation: suspend (Long) -> Unit) {
        coroutineScope.launch {
            mutex.withLock {
                if (isFeatureEnabled()) {
                    getActiveFlowId(tabId)?.let { flowId -> operation(flowId) }
                }
            }
        }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        androidBrowserConfigFeature.get().sendPageLoadWideEvent().isEnabled()
    }

    private fun getActiveFlowId(tabId: String): Long? {
        val state = activeFlows[tabId]
        if (state == null) {
            logcat { "No active flow found for tabId=$tabId" }
        }
        return state?.flowId
    }

    private fun popActiveFlowId(tabId: String): Long? {
        val state = activeFlows.remove(tabId)
        if (state == null) {
            logcat { "No active flow found to pop for tabId=$tabId" }
        }
        return state?.flowId
    }

    private inner class PageLoadState(
        val flowId: Long,
        val url: String,
        val createdAt: Long = currentTimeProvider.currentTimeMillis(),
    )

    private companion object {
        const val ABOUT_BLANK = "about:blank"
        val CLEANUP_TIMEOUT = 5.minutes
        const val PAGE_LOAD_FEATURE_NAME = "page-load"
        const val STEP_PAGE_START = "page_start"
        const val STEP_PAGE_VISIBLE = "page_visible"
        const val STEP_PAGE_ESCAPED_FIXED_PROGRESS = "page_escaped_fixed_progress"
        const val STEP_PAGE_FINISH = "page_finish"
        const val KEY_ELAPSED_TIME_TO_FINISH = "elapsed_time_to_finish_ms_bucketed"
        const val KEY_ELAPSED_TIME_TO_VISIBLE = "elapsed_time_to_visible_ms_bucketed"
        const val KEY_ELAPSED_TIME_TO_ESCAPED_FIXED_PROGRESS = "elapsed_time_to_escaped_fixed_progress_ms_bucketed"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTCOME = "outcome"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_WEBVIEW_VERSION = "webview_version"
        const val KEY_CPM_ENABLED = "cpm_enabled"
        const val KEY_TRACKER_OPTIMIZATION_ENABLED = "tracker_optimization_enabled_v2"
        const val KEY_IS_TAB_IN_FOREGROUND_ON_FINISH = "is_tab_in_foreground_on_finish"
        const val KEY_ACTIVE_REQUESTS_ON_LOAD_START = "active_requests_on_load_start"
        const val KEY_CONCURRENT_REQUESTS_ON_FINISH = "concurrent_requests_on_finish"
        const val FIXED_PROGRESS_THRESHOLD = 50
    }
}
