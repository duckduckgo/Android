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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.pixels.remoteconfig.OptimizeTrackerEvaluationRCWrapper
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PageLoadWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val webViewVersionProvider: WebViewVersionProvider = mock()
    private val autoconsent: Autoconsent = mock()
    private val optimizeTrackerEvaluationRCWrapper: OptimizeTrackerEvaluationRCWrapper = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val androidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private lateinit var pageLoadWideEvent: PageLoadWideEvent

    @Before
    fun setup() = runTest {
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("120")
        whenever(autoconsent.isAutoconsentEnabled()).thenReturn(true)
        whenever(optimizeTrackerEvaluationRCWrapper.enabled).thenReturn(true)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1000L)

        // Enable feature toggle by default
        androidBrowserConfigFeature.sendPageLoadWideEvent().setRawStoredState(Toggle.State(true))

        // Mock all WideEventClient methods to return successful Results
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any())).thenReturn(Result.success(123L))
        whenever(wideEventClient.flowStep(any(), any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalStart(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalEnd(any(), any())).thenReturn(Result.success(java.time.Duration.ofMillis(100)))
        whenever(wideEventClient.flowFinish(any(), any(), any())).thenReturn(Result.success(Unit))

        pageLoadWideEvent = RealPageLoadWideEvent(
            wideEventClient = wideEventClient,
            webViewVersionProvider = webViewVersionProvider,
            autoconsent = autoconsent,
            optimizeTrackerEvaluationRCWrapper = optimizeTrackerEvaluationRCWrapper,
            androidBrowserConfigFeature = { androidBrowserConfigFeature },
            currentTimeProvider = currentTimeProvider,
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when onPageStarted called then starts flow records step and starts interval timers`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))

        pageLoadWideEvent.onPageStarted("tab_1", "https://reddit.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "page-load",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = CleanupPolicy.OnTimeout(5.minutes.toJavaDuration()),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "page_start",
            success = true,
            metadata = emptyMap(),
        )
        verify(wideEventClient).intervalStart(123L, "elapsed_time_to_finish_ms_bucketed", null)
        verify(wideEventClient).intervalStart(123L, "elapsed_time_to_visible_ms_bucketed", null)
        verify(wideEventClient).intervalStart(123L, "elapsed_time_to_escaped_fixed_progress_ms_bucketed", null)
    }

    @Test
    fun `when onPageVisible called then ends time_to_visible interval and records step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(456L))

        pageLoadWideEvent.onPageStarted("tab_2", "https://twitter.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_2", "https://twitter.com", 50)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(456L, "elapsed_time_to_visible_ms_bucketed")
        verify(wideEventClient).flowStep(
            wideEventId = 456L,
            stepName = "page_visible",
            success = true,
            metadata = mapOf("progress" to "true"),
        )
    }

    @Test
    fun `when onPageVisible called with unknown tab then does nothing`() = runTest {
        pageLoadWideEvent.onPageVisible("unknown_tab", "https://unknown.com", 50)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }

    @Test
    fun `when onProgressChanged called then ends time_to_escaped_fixed_progress interval and records step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(789L))

        pageLoadWideEvent.onPageStarted("tab_3", "https://reddit.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onProgressChanged("tab_3", "https://reddit.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(789L, "elapsed_time_to_escaped_fixed_progress_ms_bucketed")
        verify(wideEventClient).flowStep(
            wideEventId = 789L,
            stepName = "page_escaped_fixed_progress",
        )
    }

    @Test
    fun `when onProgressChanged called with unknown tab then does nothing`() = runTest {
        pageLoadWideEvent.onProgressChanged("unknown_tab", "https://unknown.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }

    @Test
    fun `when onPageLoadFinished called with success then ends interval and records step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(999L))

        pageLoadWideEvent.onPageStarted("tab_4", "https://espn.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_4",
            url = "https://espn.com",
            errorDescription = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 5,
            concurrentRequestsOnFinish = 2,
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(999L, "elapsed_time_to_finish_ms_bucketed")
        verify(wideEventClient).flowStep(
            wideEventId = 999L,
            stepName = "page_finish",
            success = true,
            metadata = mapOf("outcome" to "success"),
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 999L,
            status = FlowStatus.Success,
            metadata = mapOf(
                "webview_version" to "120",
                "cpm_enabled" to "true",
                "tracker_optimization_enabled_v2" to "true",
                "is_tab_in_foreground_on_finish" to "true",
                "active_requests_on_load_start" to "5",
                "concurrent_requests_on_finish" to "2",
            ),
        )
    }

    @Test
    fun `when onPageLoadFinished called with error then includes error code in step metadata`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(888L))

        pageLoadWideEvent.onPageStarted("tab_5", "https://wikipedia.org")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_5",
            url = "https://wikipedia.org",
            errorDescription = "ERROR_HOST_LOOKUP",
            isTabInForegroundOnFinish = false,
            activeRequestsOnLoadStart = 3,
            concurrentRequestsOnFinish = 0,
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = 888L,
            stepName = "page_finish",
            success = false,
            metadata = mapOf(
                "outcome" to "error",
                "error_code" to "ERROR_HOST_LOOKUP",
            ),
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 888L,
            status = FlowStatus.Failure("ERROR_HOST_LOOKUP"),
            metadata = mapOf(
                "webview_version" to "120",
                "cpm_enabled" to "true",
                "tracker_optimization_enabled_v2" to "true",
                "is_tab_in_foreground_on_finish" to "false",
                "active_requests_on_load_start" to "3",
                "concurrent_requests_on_finish" to "0",
            ),
        )
    }

    @Test
    fun `when onPageLoadFinished called with unknown tab then does nothing`() = runTest {
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "unknown_tab",
            url = "https://unknown.com",
            errorDescription = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 0,
            concurrentRequestsOnFinish = 0,
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when feature disabled then results in no interactions`() = runTest {
        androidBrowserConfigFeature.sendPageLoadWideEvent().setRawStoredState(Toggle.State(false))

        pageLoadWideEvent.onPageStarted("tab_9", "https://github.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_9", "https://github.com", 50)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onProgressChanged("tab_9", "https://github.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_9",
            url = "https://github.com",
            errorDescription = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 0,
            concurrentRequestsOnFinish = 0,
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `when multiple tabs load then have independent flows`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(100L))
            .thenReturn(Result.success(200L))

        pageLoadWideEvent.onPageStarted("tab_a", "https://ebay.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageStarted("tab_b", "https://weather.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        pageLoadWideEvent.onPageVisible("tab_a", "https://ebay.com", 30)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_b", "https://weather.com", 40)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = 100L,
            stepName = "page_visible",
            success = true,
            metadata = mapOf("progress" to "false"),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 200L,
            stepName = "page_visible",
            success = true,
            metadata = mapOf("progress" to "false"),
        )
    }

    @Test
    fun `when flowStart fails then handled gracefully`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.failure(Exception("Flow start failed")))

        pageLoadWideEvent.onPageStarted("tab_fail", "https://reddit.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Should not crash and should not call flowStep
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).intervalStart(any(), any(), any())
    }

    @Test
    fun `when complete page load lifecycle then tracks all phases`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(500L))

        // Start page load
        pageLoadWideEvent.onPageStarted("tab_complete", "https://duckduckgo.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Page becomes visible
        pageLoadWideEvent.onPageVisible("tab_complete", "https://duckduckgo.com", 45)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Page escapes fixed progress
        pageLoadWideEvent.onProgressChanged("tab_complete", "https://duckduckgo.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Page finishes
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_complete",
            url = "https://duckduckgo.com",
            errorDescription = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 7,
            concurrentRequestsOnFinish = 1,
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify all steps were recorded
        verify(wideEventClient).flowStep(500L, "page_start", true, emptyMap())
        verify(wideEventClient).flowStep(500L, "page_visible", true, mapOf("progress" to "false"))
        verify(wideEventClient).flowStep(500L, "page_escaped_fixed_progress", true, emptyMap())
        verify(wideEventClient).flowStep(
            wideEventId = 500L,
            stepName = "page_finish",
            success = true,
            metadata = mapOf("outcome" to "success"),
        )

        // Verify all intervals were managed
        verify(wideEventClient).intervalStart(500L, "elapsed_time_to_finish_ms_bucketed", null)
        verify(wideEventClient).intervalStart(500L, "elapsed_time_to_visible_ms_bucketed", null)
        verify(wideEventClient).intervalStart(500L, "elapsed_time_to_escaped_fixed_progress_ms_bucketed", null)
        verify(wideEventClient).intervalEnd(500L, "elapsed_time_to_visible_ms_bucketed")
        verify(wideEventClient).intervalEnd(500L, "elapsed_time_to_escaped_fixed_progress_ms_bucketed")
        verify(wideEventClient).intervalEnd(500L, "elapsed_time_to_finish_ms_bucketed")

        // Verify flow was finished
        verify(wideEventClient).flowFinish(eq(500L), eq(FlowStatus.Success), any())
    }

    @Test
    fun `when onPageStarted called with different url then aborts previous flow`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(800L))
            .thenReturn(Result.success(900L))

        // Start first page load
        pageLoadWideEvent.onPageStarted("tab_8", "https://espn.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Start second page load with different URL - should abort first flow
        pageLoadWideEvent.onPageStarted("tab_8", "https://twitter.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify first flow was aborted
        verify(wideEventClient).flowAbort(800L)

        // Verify second flow was started
        verify(wideEventClient, times(2)).flowStart(any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when onPageStarted called with same url then does not start duplicate flow`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(1000L))

        // Start page load twice with same URL
        pageLoadWideEvent.onPageStarted("tab_10", "https://reddit.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageStarted("tab_10", "https://reddit.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify flowStart was only called once and flowAbort was never called
        verify(wideEventClient, times(1)).flowStart(any(), anyOrNull(), any(), any())
        verify(wideEventClient, never()).flowAbort(any())
    }

    @Test
    fun `when onPageStarted called with about blank then does not start flow`() = runTest {
        pageLoadWideEvent.onPageStarted("tab_1", "about:blank")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify flowStart was never called
        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when onPageStarted called with untracked url then does not start flow`() = runTest {
        // example.com is not in PageLoadedSites.perfSites
        pageLoadWideEvent.onPageStarted("tab_1", "https://untracked-site.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify flowStart was never called
        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when onPageStarted called with subdomain of tracked site then starts flow`() = runTest {
        // mobile.twitter.com should be tracked since twitter.com is in perfSites
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))

        pageLoadWideEvent.onPageStarted("tab_1", "https://mobile.twitter.com/duckduckgo")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify flowStart was called
        verify(wideEventClient).flowStart(
            name = "page-load",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = CleanupPolicy.OnTimeout(5.minutes.toJavaDuration()),
        )
    }

    @Test
    fun `when onPageVisible called with untracked url then does nothing`() = runTest {
        // Start with a tracked URL
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))
        pageLoadWideEvent.onPageStarted("tab_1", "https://reddit.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Clear invocations from setup
        clearInvocations(wideEventClient)

        // Call onPageVisible with untracked URL
        pageLoadWideEvent.onPageVisible("tab_1", "https://untracked-example.com", 50)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify no wide event operations were performed
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).intervalEnd(any(), any())
    }

    @Test
    fun `when onProgressChanged called with untracked url then does nothing`() = runTest {
        // Start with a tracked URL
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))
        pageLoadWideEvent.onPageStarted("tab_1", "https://reddit.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Clear invocations from setup
        clearInvocations(wideEventClient)

        // Call onProgressChanged with untracked URL
        pageLoadWideEvent.onProgressChanged("tab_1", "https://untracked-example.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify no wide event operations were performed
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).intervalEnd(any(), any())
    }

    @Test
    fun `when onPageLoadFinished called with success and untracked url then does nothing`() = runTest {
        // Start with a tracked URL
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))
        pageLoadWideEvent.onPageStarted("tab_1", "https://reddit.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Clear invocations from setup
        clearInvocations(wideEventClient)

        // Call onPageLoadFinished with untracked URL (e.g., redirect scenario)
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_1",
            url = "https://untracked-redirect.com",
            errorDescription = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 5,
            concurrentRequestsOnFinish = 2,
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify no wide event operations were performed
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when onPageLoadFinished called with error and untracked url then does nothing`() = runTest {
        // Start with a tracked URL
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))
        pageLoadWideEvent.onPageStarted("tab_1", "https://espn.com")
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Clear invocations from setup
        clearInvocations(wideEventClient)

        // Call onPageLoadFinished with error and untracked URL
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_1",
            url = "https://untracked-error.com",
            errorDescription = "ERR_CONNECTION_REFUSED",
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 3,
            concurrentRequestsOnFinish = 0,
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify no wide event operations were performed
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }
}
