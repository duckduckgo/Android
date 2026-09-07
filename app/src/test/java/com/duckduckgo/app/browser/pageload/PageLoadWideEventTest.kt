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
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.app.statistics.wideevents.WideEventDefinition
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.contentscopescripts.api.ContentScopeOptimizations
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PageLoadWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val webViewVersionProvider: WebViewVersionProvider = mock()
    private val autoconsent: Autoconsent = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val androidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val contentScopeOptimizations = FakeContentScopeOptimizations()

    private lateinit var pageLoadWideEvent: PageLoadWideEvent

    @Before
    fun setup() = runTest {
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("120")
        whenever(autoconsent.isAutoconsentEnabled()).thenReturn(true)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1000L)

        // Enable feature toggle by default
        androidBrowserConfigFeature.sendPageLoadWideEvent().setRawStoredState(Toggle.State(true))

        // Mock all WideEventClient methods to return successful Results
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(123L))
        whenever(wideEventClient.flowStep(any(), any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalStart(any(), any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalEnd(any(), any())).thenReturn(Result.success(100.milliseconds))
        whenever(wideEventClient.flowFinish(any(), any(), any())).thenReturn(Result.success(Unit))

        pageLoadWideEvent = RealPageLoadWideEvent(
            wideEventClient = wideEventClient,
            webViewVersionProvider = webViewVersionProvider,
            autoconsent = autoconsent,
            contentScopeOptimizations = contentScopeOptimizations,
            androidBrowserConfigFeature = { androidBrowserConfigFeature },
            currentTimeProvider = currentTimeProvider,
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when onPageStarted called then starts flow records step and starts interval timers`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(123L))

        pageLoadWideEvent.onPageStarted("tab_1", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "page-load",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = CleanupPolicy.OnTimeout(5.minutes),
            definition = WideEventDefinition(version = WideEventDefinition.Version(minor = 0, patch = 1)),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "page_start",
            success = true,
            metadata = emptyMap(),
        )
        verify(wideEventClient).intervalStart(eq(123L), eq("elapsed_time_to_finish_ms_bucketed"), eq(null), any())
        verify(wideEventClient).intervalStart(eq(123L), eq("elapsed_time_to_visible_ms_bucketed"), eq(null), any())
        verify(wideEventClient).intervalStart(eq(123L), eq("elapsed_time_to_escaped_fixed_progress_ms_bucketed"), eq(null), any())
        verify(wideEventClient, never()).intervalStart(eq(123L), eq("elapsed_time_to_content_scope_experiments_ms_bucketed"), any(), any())
        verify(wideEventClient, never()).intervalStart(eq(123L), eq("elapsed_time_to_js_injection_complete_ms_bucketed"), any(), any())
    }

    @Test
    fun `when content scope measurements taken then bucketed durations are recorded as step metadata`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(777L))
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(1_000L, 1_030L, 1_260L)

        pageLoadWideEvent.onPageStarted("tab_cs", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onContentScopeExperimentsResolved("tab_cs", 1L)
        pageLoadWideEvent.onJsInjectionComplete("tab_cs", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = 777L,
            stepName = "page_content_scope_experiments_resolved",
            metadata = mapOf("elapsed_time_to_content_scope_experiments_ms_bucketed" to "25"),
        )
        verify(wideEventClient).flowStep(
            wideEventId = 777L,
            stepName = "page_js_injection_complete",
            metadata = mapOf("elapsed_time_to_js_injection_complete_ms_bucketed" to "200"),
        )
    }

    @Test
    fun `when measurement is faster than the smallest bucket then it is recorded as zero`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(777L))
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(1_000L, 1_002L)

        pageLoadWideEvent.onPageStarted("tab_cs", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onContentScopeExperimentsResolved("tab_cs", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = 777L,
            stepName = "page_content_scope_experiments_resolved",
            metadata = mapOf("elapsed_time_to_content_scope_experiments_ms_bucketed" to "0"),
        )
    }

    @Test
    fun `when a measurement is taken twice for the same navigation then the first one is kept`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(778L))
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(1_000L, 1_030L, 1_500L)

        pageLoadWideEvent.onPageStarted("tab_cs", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onJsInjectionComplete("tab_cs", 1L)
        pageLoadWideEvent.onJsInjectionComplete("tab_cs", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, times(1)).flowStep(eq(778L), eq("page_js_injection_complete"), any(), any())
        verify(wideEventClient).flowStep(
            wideEventId = 778L,
            stepName = "page_js_injection_complete",
            metadata = mapOf("elapsed_time_to_js_injection_complete_ms_bucketed" to "25"),
        )
    }

    @Test
    fun `when a measurement is taken before the flow start is processed then it is still recorded`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(780L))
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(1_000L, 1_060L)

        // No advanceUntilIdle in between: the measurement is submitted to the serialized scope while the flow start is
        // still queued ahead of it, which is what happens in production.
        pageLoadWideEvent.onPageStarted("tab_cs", "https://reddit.com", 1L)
        pageLoadWideEvent.onJsInjectionComplete("tab_cs", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = 780L,
            stepName = "page_js_injection_complete",
            metadata = mapOf("elapsed_time_to_js_injection_complete_ms_bucketed" to "50"),
        )
    }

    @Test
    fun `when measurement is for a navigation the flow did not start with then it is not recorded`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(779L))
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(1_000L, 1_030L)

        pageLoadWideEvent.onPageStarted("tab_cs", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        // A later navigation in this tab reports its injection. Dropping it is deliberate: this flow was started for a
        // different navigation, so measuring from its page start would report a duration that never happened.
        pageLoadWideEvent.onJsInjectionComplete("tab_cs", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).flowStep(any(), eq("page_js_injection_complete"), any(), any())
    }

    @Test
    fun `when a stale measurement arrives after the same url reloaded then the new flow keeps its own`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(783L))
            .thenReturn(Result.success(784L))
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(1_000L, 5_000L, 5_040L, 5_100L)

        pageLoadWideEvent.onPageStarted("tab_cs", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        finishPageLoad("tab_cs", 1L)
        // The same url loads again in this tab while the first navigation's deferred callback is still pending, so the
        // late measurement arrives with a live flow in place for the same tab and url.
        pageLoadWideEvent.onPageStarted("tab_cs", "https://reddit.com", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onJsInjectionComplete("tab_cs", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).flowStep(any(), eq("page_js_injection_complete"), any(), any())

        pageLoadWideEvent.onJsInjectionComplete("tab_cs", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Measured from the second navigation's page start, and not blocked by the stale one having been seen first.
        verify(wideEventClient).flowStep(
            wideEventId = 784L,
            stepName = "page_js_injection_complete",
            metadata = mapOf("elapsed_time_to_js_injection_complete_ms_bucketed" to "100"),
        )
    }

    @Test
    fun `when a measurement is taken after the flow finished then it is not recorded`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(782L))
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(1_000L, 1_030L)

        pageLoadWideEvent.onPageStarted("tab_cs", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        finishPageLoad("tab_cs", 1L)
        pageLoadWideEvent.onJsInjectionComplete("tab_cs", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).flowStep(any(), eq("page_js_injection_complete"), any(), any())
    }

    @Test
    fun `when no flow is active then content scope measurements are dropped`() = runTest {
        pageLoadWideEvent.onJsInjectionComplete("tab_without_flow", 1L)
        pageLoadWideEvent.onContentScopeExperimentsResolved("tab_without_flow", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `when content scope optimizations differ then each one is reported under its own key`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(781L))
        contentScopeOptimizations.state = ContentScopeOptimizations.State(
            injectionOptimized = true,
            messagingOptimized = false,
            experimentsCached = true,
        )

        pageLoadWideEvent.onPageStarted("tab_cs", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        finishPageLoad("tab_cs", 1L)

        val metadata = capturedFlowFinishMetadata(781L)
        assertEquals("true", metadata["content_scope_injection_optimized"])
        assertEquals("false", metadata["content_scope_messaging_optimized"])
        assertEquals("true", metadata["content_scope_experiments_cached"])
    }

    private fun finishPageLoad(tabId: String, navigationId: Long) {
        pageLoadWideEvent.onPageLoadFinished(
            tabId = tabId,
            navigationId = navigationId,
            errorDescription = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 0,
            concurrentRequestsOnFinish = 0,
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
    }

    private suspend fun capturedFlowFinishMetadata(flowId: Long): Map<String, String> {
        val captor = argumentCaptor<Map<String, String>>()
        verify(wideEventClient).flowFinish(eq(flowId), any(), captor.capture())
        return captor.firstValue
    }

    @Test
    fun `when onPageVisible called then ends time_to_visible interval and records step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(456L))

        pageLoadWideEvent.onPageStarted("tab_2", "https://twitter.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_2", 1L, 50)
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
        pageLoadWideEvent.onPageVisible("unknown_tab", 1L, 50)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }

    @Test
    fun `when onProgressChanged called then ends time_to_escaped_fixed_progress interval and records step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(789L))

        pageLoadWideEvent.onPageStarted("tab_3", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onProgressChanged("tab_3", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(789L, "elapsed_time_to_escaped_fixed_progress_ms_bucketed")
        verify(wideEventClient).flowStep(
            wideEventId = 789L,
            stepName = "page_escaped_fixed_progress",
        )
    }

    @Test
    fun `when onProgressChanged called with unknown tab then does nothing`() = runTest {
        pageLoadWideEvent.onProgressChanged("unknown_tab", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }

    @Test
    fun `when onPageLoadFinished called with success then ends interval and records step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(999L))

        pageLoadWideEvent.onPageStarted("tab_4", "https://espn.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_4",
            navigationId = 1L,
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
                "is_tab_in_foreground_on_finish" to "true",
                "active_requests_on_load_start" to "5",
                "concurrent_requests_on_finish" to "2",
                "content_scope_injection_optimized" to "true",
                "content_scope_messaging_optimized" to "true",
                "content_scope_experiments_cached" to "true",
            ),
        )
    }

    @Test
    fun `when onPageLoadFinished called with error then includes error code in step metadata`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(888L))

        pageLoadWideEvent.onPageStarted("tab_5", "https://wikipedia.org", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_5",
            navigationId = 1L,
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
                "is_tab_in_foreground_on_finish" to "false",
                "active_requests_on_load_start" to "3",
                "concurrent_requests_on_finish" to "0",
                "content_scope_injection_optimized" to "true",
                "content_scope_messaging_optimized" to "true",
                "content_scope_experiments_cached" to "true",
            ),
        )
    }

    @Test
    fun `when onPageLoadFinished called with unknown tab then does nothing`() = runTest {
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "unknown_tab",
            navigationId = 1L,
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

        pageLoadWideEvent.onPageStarted("tab_9", "https://github.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_9", 1L, 50)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onProgressChanged("tab_9", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_9",
            navigationId = 1L,
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
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(100L))
            .thenReturn(Result.success(200L))

        pageLoadWideEvent.onPageStarted("tab_a", "https://ebay.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageStarted("tab_b", "https://weather.com", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        pageLoadWideEvent.onPageVisible("tab_a", 1L, 30)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_b", 2L, 40)
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
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.failure(Exception("Flow start failed")))

        pageLoadWideEvent.onPageStarted("tab_fail", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Should not crash and should not call flowStep
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).intervalStart(any(), any(), any(), any())
    }

    @Test
    fun `when complete page load lifecycle then tracks all phases`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(500L))

        // Start page load
        pageLoadWideEvent.onPageStarted("tab_complete", "https://duckduckgo.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Page becomes visible
        pageLoadWideEvent.onPageVisible("tab_complete", 1L, 45)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Page escapes fixed progress
        pageLoadWideEvent.onProgressChanged("tab_complete", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Page finishes
        pageLoadWideEvent.onPageLoadFinished(
            tabId = "tab_complete",
            navigationId = 1L,
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
        verify(wideEventClient).intervalStart(eq(500L), eq("elapsed_time_to_finish_ms_bucketed"), eq(null), any())
        verify(wideEventClient).intervalStart(eq(500L), eq("elapsed_time_to_visible_ms_bucketed"), eq(null), any())
        verify(wideEventClient).intervalStart(eq(500L), eq("elapsed_time_to_escaped_fixed_progress_ms_bucketed"), eq(null), any())
        verify(wideEventClient).intervalEnd(500L, "elapsed_time_to_visible_ms_bucketed")
        verify(wideEventClient).intervalEnd(500L, "elapsed_time_to_escaped_fixed_progress_ms_bucketed")
        verify(wideEventClient).intervalEnd(500L, "elapsed_time_to_finish_ms_bucketed")

        // Verify flow was finished
        verify(wideEventClient).flowFinish(eq(500L), eq(FlowStatus.Success), any())
    }

    @Test
    fun `when onPageStarted called with different url then cancels previous flow`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(800L))
            .thenReturn(Result.success(900L))

        // Start first page load
        pageLoadWideEvent.onPageStarted("tab_8", "https://espn.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Start second page load with different URL - should cancel first flow
        pageLoadWideEvent.onPageStarted("tab_8", "https://twitter.com", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 800L,
            status = FlowStatus.Cancelled,
            metadata = mapOf(
                "webview_version" to "120",
                "cpm_enabled" to "true",
                "content_scope_injection_optimized" to "true",
                "content_scope_messaging_optimized" to "true",
                "content_scope_experiments_cached" to "true",
                "cancellation_reason" to "superseded_different_url",
            ),
        )

        // Verify second flow was started
        verify(wideEventClient, times(2)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when a load is replaced by one of the same url then it is cancelled as a restart`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(801L))
            .thenReturn(Result.success(802L))

        pageLoadWideEvent.onPageStarted("tab_same", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        // A restart of the load rather than a navigation away from it, which the reason has to keep apart.
        pageLoadWideEvent.onPageStarted("tab_same", "https://reddit.com", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(eq(801L), eq(FlowStatus.Cancelled), any())
        assertEquals("superseded_same_url", capturedFlowFinishMetadata(801L)["cancellation_reason"])
    }

    @Test
    fun `when the load being replaced is already past the cleanup timeout then it is left to the cleanup policy`() = runTest {
        whenever(currentTimeProvider.currentTimeMillis())
            .thenReturn(1000L)
            .thenReturn(1000L + 6.minutes.inWholeMilliseconds)
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(910L))
            .thenReturn(Result.success(911L))

        pageLoadWideEvent.onPageStarted("tab_stale", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageStarted("tab_stale", "https://ebay.com", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Cancelled means superseded while it was still being measured. A load this old was not, so its outcome is
        // genuinely unaccounted for and the cleanup policy owns it.
        verify(wideEventClient, times(2)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(wideEventClient, never()).flowFinish(eq(910L), any(), any())
        verify(wideEventClient, never()).flowAbort(any())
    }

    @Test
    fun `when the same url loads again in a tab then it gets its own flow`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1000L))
            .thenReturn(Result.success(1001L))

        pageLoadWideEvent.onPageStarted("tab_10", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        finishPageLoad("tab_10", 1L)
        pageLoadWideEvent.onPageStarted("tab_10", "https://reddit.com", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_10", 2L, 80)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, times(2)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(wideEventClient).flowStep(1001L, "page_visible", true, mapOf("progress" to "true"))
    }

    @Test
    fun `when onPageStarted called with about blank then does not start flow`() = runTest {
        pageLoadWideEvent.onPageStarted("tab_1", "about:blank", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify flowStart was never called
        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when onPageStarted called with untracked url then does not start flow`() = runTest {
        // example.com is not in PageLoadedSites.perfSites
        pageLoadWideEvent.onPageStarted("tab_1", "https://untracked-site.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify flowStart was never called
        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when onPageStarted called with subdomain of tracked site then starts flow`() = runTest {
        // mobile.twitter.com should be tracked since twitter.com is in perfSites
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(123L))

        pageLoadWideEvent.onPageStarted("tab_1", "https://mobile.twitter.com/duckduckgo", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // Verify flowStart was called
        verify(wideEventClient).flowStart(
            name = "page-load",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = CleanupPolicy.OnTimeout(5.minutes),
            definition = WideEventDefinition(version = WideEventDefinition.Version(minor = 0, patch = 1)),
        )
    }

    @Test
    fun `when a load redirects then its events still reach the flow it started`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(123L))

        // https://espn.com redirects to https://www.espn.com/, so every callback after the redirect carries a url the
        // flow was not started with. They belong to the same load, and to the same navigationId.
        pageLoadWideEvent.onPageStarted("tab_1", "https://espn.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_1", 1L, 60)
        pageLoadWideEvent.onProgressChanged("tab_1", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        finishPageLoad("tab_1", 1L)

        verify(wideEventClient).intervalEnd(123L, "elapsed_time_to_visible_ms_bucketed")
        verify(wideEventClient).intervalEnd(123L, "elapsed_time_to_escaped_fixed_progress_ms_bucketed")
        verify(wideEventClient).intervalEnd(123L, "elapsed_time_to_finish_ms_bucketed")
        verify(wideEventClient).flowFinish(eq(123L), eq(FlowStatus.Success), any())
    }

    @Test
    fun `when events arrive from a load the flow did not start then they are dropped`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(123L))
        pageLoadWideEvent.onPageStarted("tab_1", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        clearInvocations(wideEventClient)

        pageLoadWideEvent.onPageVisible("tab_1", 2L, 50)
        pageLoadWideEvent.onProgressChanged("tab_1", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        finishPageLoad("tab_1", 2L)

        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when a page becomes visible twice in one load then the first is kept`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(123L))

        pageLoadWideEvent.onPageStarted("tab_1", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_1", 1L, 10)
        pageLoadWideEvent.onPageVisible("tab_1", 1L, 90)
        pageLoadWideEvent.onProgressChanged("tab_1", 1L)
        pageLoadWideEvent.onProgressChanged("tab_1", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        // The interval only ends once, so the progress reported alongside it has to be the one it was measured against.
        verify(wideEventClient, times(1)).flowStep(eq(123L), eq("page_visible"), any(), any())
        verify(wideEventClient).flowStep(123L, "page_visible", true, mapOf("progress" to "false"))
        verify(wideEventClient, times(1)).flowStep(eq(123L), eq("page_escaped_fixed_progress"), any(), any())
    }

    @Test
    fun `when a new load starts in a tab then the previous flow is cancelled even if untracked`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(123L))

        pageLoadWideEvent.onPageStarted("tab_1", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        // Left open it would be swept by the cleanup policy and sent as an Unknown load with no duration.
        pageLoadWideEvent.onPageStarted("tab_1", "https://untracked-site.com", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertEquals("superseded_different_url", capturedFlowFinishMetadata(123L)["cancellation_reason"])
        verify(wideEventClient).flowFinish(eq(123L), eq(FlowStatus.Cancelled), any())
        verify(wideEventClient, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when a page becomes visible before its flow exists then the measurement still lands`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(123L))

        // Queued together, so flowStart has not run when the commit-visible callback arrives - the ordinary ordering
        // for a cached page, since flowStart goes to disk. Gating these callbacks on an activeFlows hit outside the
        // lock would drop them.
        pageLoadWideEvent.onPageStarted("tab_early", "https://reddit.com", 1L)
        pageLoadWideEvent.onPageVisible("tab_early", 1L, 80)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(123L, "elapsed_time_to_visible_ms_bucketed")
        verify(wideEventClient).flowStep(123L, "page_visible", true, mapOf("progress" to "true"))
    }

    @Test
    fun `when an untracked load starts before the flow it replaces exists then that flow is still cancelled`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(123L))

        // Both starts are queued before either reaches the lock, which is what a tracked load redirecting off its
        // domain looks like: the second start arrives while the flow it has to end is still being created.
        pageLoadWideEvent.onPageStarted("tab_race", "https://reddit.com", 1L)
        pageLoadWideEvent.onPageStarted("tab_race", "https://untracked-site.com", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(eq(123L), eq(FlowStatus.Cancelled), any())
    }

    @Test
    fun `when a load is replaced mid flight then each load is measured on its own`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(73L))
            .thenReturn(Result.success(74L))
            .thenReturn(Result.success(75L))

        // reddit.com starts loading and the user navigates away to ebay.com before it finishes.
        pageLoadWideEvent.onPageStarted("tab_r", "https://reddit.com", 1L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageStarted("tab_r", "https://ebay.com", 2L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        finishPageLoad("tab_r", 2L)

        // Within the cleanup timeout, reddit.com loads again in the same tab.
        pageLoadWideEvent.onPageStarted("tab_r", "https://reddit.com", 3L)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        pageLoadWideEvent.onPageVisible("tab_r", 3L, 70)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        finishPageLoad("tab_r", 3L)

        // The abandoned reddit load is reported as superseded, rather than as an event whose duration spans it and
        // ebay together, or as nothing at all.
        verify(wideEventClient).flowFinish(eq(73L), eq(FlowStatus.Cancelled), any())
        verify(wideEventClient, never()).intervalEnd(eq(73L), eq("elapsed_time_to_finish_ms_bucketed"))
        verify(wideEventClient).intervalEnd(74L, "elapsed_time_to_finish_ms_bucketed")
        verify(wideEventClient).flowFinish(eq(74L), eq(FlowStatus.Success), any())

        // The reload is measured in full instead of being swallowed by the flow the first attempt left behind.
        verify(wideEventClient).flowStep(75L, "page_visible", true, mapOf("progress" to "true"))
        verify(wideEventClient).intervalEnd(75L, "elapsed_time_to_visible_ms_bucketed")
        verify(wideEventClient).intervalEnd(75L, "elapsed_time_to_finish_ms_bucketed")
        verify(wideEventClient).flowFinish(eq(75L), eq(FlowStatus.Success), any())
    }

    private class FakeContentScopeOptimizations : ContentScopeOptimizations {
        var state = ContentScopeOptimizations.State(
            injectionOptimized = true,
            messagingOptimized = true,
            experimentsCached = true,
        )

        override suspend fun current(): ContentScopeOptimizations.State = state
    }
}
