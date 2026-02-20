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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class PageLoadWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()
    private val webViewVersionProvider: WebViewVersionProvider = mock()
    private val autoconsent: Autoconsent = mock()
    private val optimizeTrackerEvaluationRCWrapper: OptimizeTrackerEvaluationRCWrapper = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()

    private lateinit var pageLoadWideEvent: PageLoadWideEvent

    @Before
    fun setup() = runTest {
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("120")
        whenever(autoconsent.isAutoconsentEnabled()).thenReturn(true)
        whenever(optimizeTrackerEvaluationRCWrapper.enabled).thenReturn(true)

        val mockToggle = mock<Toggle>()
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(androidBrowserConfigFeature.sendPageLoadWideEvent()).thenReturn(mockToggle)

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
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when startPageLoad called then starts flow records step and starts interval timers`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))

        pageLoadWideEvent.startPageLoad("tab_1", "https://example.com")

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
    fun `when recordPageVisible called then ends time_to_visible interval and records step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(456L))

        pageLoadWideEvent.startPageLoad("tab_2", "https://twitter.com")
        pageLoadWideEvent.recordPageVisible("tab_2", 50)

        verify(wideEventClient).intervalEnd(456L, "elapsed_time_to_visible_ms_bucketed")
        verify(wideEventClient).flowStep(
            wideEventId = 456L,
            stepName = "page_visible",
            success = true,
            metadata = mapOf("progress" to "50"),
        )
    }

    @Test
    fun `when recordPageVisible called with unknown tab then does nothing`() = runTest {
        pageLoadWideEvent.recordPageVisible("unknown_tab", 50)

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }

    @Test
    fun `when recordExitedFixedProgress called then ends time_to_escaped_fixed_progress interval and records step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(789L))

        pageLoadWideEvent.startPageLoad("tab_3", "https://reddit.com")
        pageLoadWideEvent.recordExitedFixedProgress("tab_3", 55)

        verify(wideEventClient).intervalEnd(789L, "elapsed_time_to_escaped_fixed_progress_ms_bucketed")
        verify(wideEventClient).flowStep(
            wideEventId = 789L,
            stepName = "page_escaped_fixed_progress",
            success = true,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `when recordExitedFixedProgress called with unknown tab then does nothing`() = runTest {
        pageLoadWideEvent.recordExitedFixedProgress("unknown_tab", 60)

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }

    @Test
    fun `when finishPageLoad called with success then ends interval and records step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(999L))

        pageLoadWideEvent.startPageLoad("tab_4", "https://espn.com")
        pageLoadWideEvent.finishPageLoad(
            tabId = "tab_4",
            outcome = "success",
            errorCode = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 5,
            concurrentRequestsOnFinish = 2,
        )

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
    fun `when finishPageLoad called with error then includes error code in step metadata`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(888L))

        pageLoadWideEvent.startPageLoad("tab_5", "https://wikipedia.org")
        pageLoadWideEvent.finishPageLoad(
            tabId = "tab_5",
            outcome = "error",
            errorCode = "ERROR_HOST_LOOKUP",
            isTabInForegroundOnFinish = false,
            activeRequestsOnLoadStart = 3,
            concurrentRequestsOnFinish = 0,
        )

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
            status = FlowStatus.Failure("error"),
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
    fun `when finishPageLoad called with unknown tab then does nothing`() = runTest {
        pageLoadWideEvent.finishPageLoad(
            tabId = "unknown_tab",
            outcome = "success",
            errorCode = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 0,
            concurrentRequestsOnFinish = 0,
        )

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when feature enabled then results in no interactions`() = runTest {
        val enabledToggle = mock<Toggle>()
        whenever(enabledToggle.isEnabled()).thenReturn(false)
        whenever(androidBrowserConfigFeature.sendPageLoadWideEvent()).thenReturn(enabledToggle)

        pageLoadWideEvent.startPageLoad("tab_9", "https://github.com")
        pageLoadWideEvent.recordPageVisible("tab_9", 50)
        pageLoadWideEvent.recordExitedFixedProgress("tab_9", 55)
        pageLoadWideEvent.finishPageLoad(
            tabId = "tab_9",
            outcome = "success",
            errorCode = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 0,
            concurrentRequestsOnFinish = 0,
        )

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `when multiple tabs load then have independent flows`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(100L))
            .thenReturn(Result.success(200L))

        pageLoadWideEvent.startPageLoad("tab_a", "https://ebay.com")
        pageLoadWideEvent.startPageLoad("tab_b", "https://weather.com")

        pageLoadWideEvent.recordPageVisible("tab_a", 30)
        pageLoadWideEvent.recordPageVisible("tab_b", 40)

        verify(wideEventClient).flowStep(
            wideEventId = 100L,
            stepName = "page_visible",
            success = true,
            metadata = mapOf("progress" to "30"),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 200L,
            stepName = "page_visible",
            success = true,
            metadata = mapOf("progress" to "40"),
        )
    }

    @Test
    fun `when flowStart fails then handled gracefully`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.failure(Exception("Flow start failed")))

        pageLoadWideEvent.startPageLoad("tab_fail", "https://failsite.com")

        // Should not crash and should not call flowStep
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).intervalStart(any(), any(), any())
    }

    @Test
    fun `when complete page load lifecycle then tracks all phases`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(500L))

        // Start page load
        pageLoadWideEvent.startPageLoad("tab_complete", "https://duckduckgo.com")

        // Page becomes visible
        pageLoadWideEvent.recordPageVisible("tab_complete", 45)

        // Page escapes fixed progress
        pageLoadWideEvent.recordExitedFixedProgress("tab_complete", 52)

        // Page finishes
        pageLoadWideEvent.finishPageLoad(
            tabId = "tab_complete",
            outcome = "success",
            errorCode = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 7,
            concurrentRequestsOnFinish = 1,
        )

        // Verify all steps were recorded
        verify(wideEventClient).flowStep(500L, "page_start", true, emptyMap())
        verify(wideEventClient).flowStep(500L, "page_visible", true, mapOf("progress" to "45"))
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
    fun `when isInProgress called with matching url then returns true`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(600L))

        pageLoadWideEvent.startPageLoad("tab_6", "https://espn.com")

        assertTrue(pageLoadWideEvent.isInProgress("tab_6", "https://espn.com"))
    }

    @Test
    fun `when isInProgress called with different url then returns false`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(700L))

        pageLoadWideEvent.startPageLoad("tab_7", "https://espn.com")

        assertFalse(pageLoadWideEvent.isInProgress("tab_7", "https://espn.co.uk"))
    }

    @Test
    fun `when isInProgress called with unknown tab then returns false`() = runTest {
        assertFalse(pageLoadWideEvent.isInProgress("unknown_tab", "https://example.com"))
    }

    @Test
    fun `when startPageLoad called with different url then aborts previous flow`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(800L))
            .thenReturn(Result.success(900L))

        // Start first page load
        pageLoadWideEvent.startPageLoad("tab_8", "https://espn.com")

        // Start second page load with different URL - should abort first flow
        pageLoadWideEvent.startPageLoad("tab_8", "https://espn.co.uk")

        // Verify first flow was aborted
        verify(wideEventClient).flowAbort(800L)

        // Verify second flow was started
        verify(wideEventClient, times(2)).flowStart(any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when startPageLoad called with same url then does not abort previous flow`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(1000L))

        // Start page load twice with same URL
        pageLoadWideEvent.startPageLoad("tab_10", "https://reddit.com")
        assertTrue(pageLoadWideEvent.isInProgress("tab_10", "https://reddit.com"))

        // Verify flowAbort was never called
        verify(wideEventClient, never()).flowAbort(any())
    }
}
