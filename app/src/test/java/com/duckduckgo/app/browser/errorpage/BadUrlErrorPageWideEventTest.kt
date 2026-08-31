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

import com.duckduckgo.app.browser.customtabs.CustomTabViewModel.Companion.CUSTOM_TAB_NAME_PREFIX
import com.duckduckgo.app.browser.suggestredirect.SuggestRedirectOnUnresolvedErrorFeature
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class BadUrlErrorPageWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val badUrlErrorPageWideEventFeature = FakeFeatureToggleFactory.create(BadUrlErrorPageWideEventFeature::class.java)
    private val suggestRedirectFeature = FakeFeatureToggleFactory.create(SuggestRedirectOnUnresolvedErrorFeature::class.java)
    private val regularTabRepository: TabRepository = mock()
    private val fireTabRepository: TabRepository = mock()
    private val openTabs = MutableStateFlow(listOf(TabEntity(tabId = TAB_ID), TabEntity(tabId = OTHER_TAB_ID)))

    private lateinit var testee: RealBadUrlErrorPageWideEvent

    @Before
    fun setup() = runTest {
        badUrlErrorPageWideEventFeature.self().setRawStoredState(Toggle.State(enable = true))
        suggestRedirectFeature.self().setRawStoredState(Toggle.State(enable = true))
        suggestRedirectFeature.suggestRedirect().setRawStoredState(Toggle.State(enable = true))

        whenever(
            wideEventClient.flowStart(
                any(),
                anyOrNull(),
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(Result.success(FLOW_ID))
        whenever(
            wideEventClient.flowStep(
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(Result.success(Unit))
        whenever(
            wideEventClient.intervalStart(
                any(),
                any(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(Result.success(Unit))
        whenever(
            wideEventClient.intervalEnd(
                any(),
                any(),
            ),
        ).thenReturn(Result.success(100.milliseconds))
        whenever(
            wideEventClient.flowFinish(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(Result.success(Unit))

        whenever(regularTabRepository.flowTabs).thenReturn(openTabs)
        whenever(fireTabRepository.flowTabs).thenReturn(MutableStateFlow(emptyList()))

        testee = RealBadUrlErrorPageWideEvent(
            wideEventClient = wideEventClient,
            badUrlErrorPageWideEventFeature = { badUrlErrorPageWideEventFeature },
            suggestRedirectFeature = suggestRedirectFeature,
            regularTabRepository = regularTabRepository,
            fireTabRepository = fireTabRepository,
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    //region Wide event Feature Flag tests
    @Test
    fun `when wide event feature disabled, then error page displayed does not start flow`() = runTest {
        badUrlErrorPageWideEventFeature.self().setRawStoredState(Toggle.State(enable = false))

        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, never()).flowStart(
            any(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `when suggest redirect feature disabled, then error page displayed does not start flow`() = runTest {
        suggestRedirectFeature.suggestRedirect().setRawStoredState(Toggle.State(enable = false))

        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, never()).flowStart(
            any(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `when feature disabled mid-flow, then active flows still finish`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()
        badUrlErrorPageWideEventFeature.self().setRawStoredState(Toggle.State(enable = false))

        testee.onBadUrlErrorPageExited(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "abandoned"),
        )
    }

    @Test
    fun `when feature disabled mid-flow, then a repeat BAD_URL after redirect click still finishes as failure without a new flow`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectClicked(TAB_ID)
        advanceUntilIdle()
        badUrlErrorPageWideEventFeature.self().setRawStoredState(Toggle.State(enable = false))

        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Failure(reason = "new_hostname_resolution_failed"),
            metadata = emptyMap(),
        )
        verify(wideEventClient, times(1)).flowStart(
            any(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
        )
    }
    //endregion

    //region Wide event flow start tests
    @Test
    fun `when error page displayed, then flow started and duration interval started`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "bad-url-error-page",
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
        verify(wideEventClient).intervalStart(
            eq(FLOW_ID),
            eq("error_page_duration_ms_bucketed"),
            anyOrNull(),
            any(),
        )
    }

    @Test
    fun `when error page displayed while flow active for tab, then no new flow started`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, times(1)).flowStart(
            any(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `when error page displayed on two tabs, then each tab tracked by its own flow`() = runTest {
        whenever(
            wideEventClient.flowStart(
                any(),
                anyOrNull(),
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(
            Result.success(FLOW_ID),
            Result.success(OTHER_FLOW_ID),
        )

        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onBadUrlErrorPageDisplayed(OTHER_TAB_ID)
        testee.onBadUrlErrorPageExited(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, times(2)).flowStart(
            any(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
        )
        verify(wideEventClient).flowFinish(
            eq(FLOW_ID),
            any(),
            any(),
        )
        verify(wideEventClient, never()).flowFinish(
            eq(OTHER_FLOW_ID),
            any(),
            any(),
        )
    }

    @Test
    fun `when error page displayed after exit, then new flow started`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onBadUrlErrorPageExited(TAB_ID)
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, times(2)).flowStart(
            any(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `when flow start fails, then subsequent events are no-ops`() = runTest {
        whenever(
            wideEventClient.flowStart(
                any(),
                anyOrNull(),
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(Result.failure(RuntimeException("Error!")))

        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectSuggested(TAB_ID)
        testee.onBadUrlErrorPageExited(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, never()).flowStep(
            any(),
            any(),
            any(),
            any(),
        )
        verify(wideEventClient, never()).flowFinish(
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `when error page displayed on a custom tab, then no wide event is started`() = runTest {
        testee.onBadUrlErrorPageDisplayed(CUSTOM_TAB_ID)
        testee.onRedirectSuggested(CUSTOM_TAB_ID)
        testee.onBadUrlErrorPageExited(CUSTOM_TAB_ID)
        advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }
    //endregion

    //region No active flow tests
    @Test
    fun `when events arrive without an active flow, then nothing is recorded`() = runTest {
        testee.onRedirectSuggested(TAB_ID)
        testee.onRedirectClicked(TAB_ID)
        testee.onOtherErrorPageDisplayed(TAB_ID)
        testee.onOmittedErrorReceived(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        testee.onErrorPageRefreshed(TAB_ID)
        testee.onBadUrlErrorPageExited(TAB_ID)
        advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }
    //endregion

    //region Wide event Redirect Suggested step tests
    @Test
    fun `when redirect suggested, then step recorded once with last_step metadata`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectSuggested(TAB_ID)
        testee.onRedirectSuggested(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, times(1)).flowStep(
            wideEventId = FLOW_ID,
            stepName = "redirect_suggested",
            metadata = mapOf("last_step" to "redirect_suggested"),
        )
    }

    @Test
    fun `when redirect suggested again after a kept retry error, then step not recorded again`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectSuggested(TAB_ID)
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectSuggested(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, times(1)).flowStep(
            any(),
            any(),
            any(),
            any(),
        )
    }
    //endregion

    //region Wide event Redirect Clicked step tests
    @Test
    fun `when redirect clicked, then step recorded and flow stays open awaiting the outcome`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectClicked(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = FLOW_ID,
            stepName = "redirect_clicked",
            metadata = mapOf("last_step" to "redirect_clicked"),
        )
        verify(wideEventClient, never()).intervalEnd(
            any(),
            any(),
        )
        verify(wideEventClient, never()).flowFinish(
            any(),
            any(),
            any(),
        )
    }
    //endregion

    //region Wide event flow finish tests
    @Test
    fun `when page load finishes after redirect clicked, then flow finished with success`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectClicked(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).intervalEnd(
            FLOW_ID,
            "error_page_duration_ms_bucketed",
        )
        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Success,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `when other error page displayed after redirect clicked, then flow finished with success`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectClicked(TAB_ID)
        testee.onOtherErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Success,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `when other error page displayed without redirect clicked, then flow cancelled as error_replaced_on_refresh`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onOtherErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "error_replaced_on_refresh"),
        )
    }

    @Test
    fun `when new BAD_URL error displayed after redirect clicked, then flow finished with failure and a new flow started`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectClicked(TAB_ID)
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Failure(reason = "new_hostname_resolution_failed"),
            metadata = emptyMap(),
        )
        verify(wideEventClient, times(2)).flowStart(
            any(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
        )
        verify(wideEventClient, times(2)).intervalStart(
            any(),
            any(),
            anyOrNull(),
            any(),
        )
    }

    @Test
    fun `when error page exited after redirect clicked, then flow finished as abandoned`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectClicked(TAB_ID)
        testee.onBadUrlErrorPageExited(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "abandoned"),
        )
    }

    @Test
    fun `when error page exited, then flow finished as abandoned`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onBadUrlErrorPageExited(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).intervalEnd(
            FLOW_ID,
            "error_page_duration_ms_bucketed",
        )
        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "abandoned"),
        )
    }

    @Test
    fun `when page load finishes without omitted error after error page refreshed, then flow finished as recovered_on_refresh`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onErrorPageRefreshed(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).intervalEnd(
            FLOW_ID,
            "error_page_duration_ms_bucketed",
        )
        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "recovered_on_refresh"),
        )
    }

    @Test
    fun `when page load finishes with omitted error after error page refreshed, then flow finished as error_replaced_on_refresh`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onErrorPageRefreshed(TAB_ID)
        testee.onOmittedErrorReceived(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "error_replaced_on_refresh"),
        )
    }

    @Test
    fun `when omitted error received before refresh, then refresh outcome still recovered_on_refresh`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onOmittedErrorReceived(TAB_ID)
        testee.onErrorPageRefreshed(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "recovered_on_refresh"),
        )
    }

    @Test
    fun `when page load finishes without redirect clicked, then flow not finished`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, never()).flowFinish(
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `when page load finishes with omitted error without refresh or redirect click, then flow not finished`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onOmittedErrorReceived(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, never()).flowFinish(
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `when page load finishes with omitted error after redirect clicked, then flow finished with success`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectClicked(TAB_ID)
        testee.onOmittedErrorReceived(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Success,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `when refresh hits the same BAD_URL error, then flow kept and a later page load does not finish it`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onErrorPageRefreshed(TAB_ID)
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, times(1)).flowStart(
            any(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
        )
        verify(wideEventClient, never()).flowFinish(
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `when page load finishes after the flow already finished, then nothing finished again`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onRedirectClicked(TAB_ID)
        testee.onBadUrlErrorPageExited(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        advanceUntilIdle()

        verify(wideEventClient, times(1)).flowFinish(
            any(),
            any(),
            any(),
        )
        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "abandoned"),
        )
    }
    //endregion

    //region Closed tab detection tests
    @Test
    fun `when tab with active flow is removed, then flow finished as abandoned`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        openTabs.value = listOf(TabEntity(tabId = OTHER_TAB_ID))
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "abandoned"),
        )
    }

    @Test
    fun `when tab without active flow is removed, then its removal finishes nothing`() = runTest {
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        openTabs.value = listOf(TabEntity(tabId = TAB_ID))
        advanceUntilIdle()

        verify(wideEventClient, never()).flowFinish(
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `when tab removed during a second flow started after the first finished, then second flow finished as abandoned`() = runTest {
        whenever(
            wideEventClient.flowStart(
                any(),
                anyOrNull(),
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(
            Result.success(FLOW_ID),
            Result.success(OTHER_FLOW_ID),
        )

        // Finishing the only active flow stops the closed-tab detection. A second flow should re-launch it
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        testee.onErrorPageRefreshed(TAB_ID)
        testee.onPageLoadFinished(TAB_ID)
        testee.onBadUrlErrorPageDisplayed(TAB_ID)
        advanceUntilIdle()

        openTabs.value = listOf(TabEntity(tabId = OTHER_TAB_ID))
        advanceUntilIdle()

        // Check that the closed-tab detection was re-launched by verifying that it triggered the second flow to finish after closing the tab
        verify(wideEventClient).flowFinish(
            wideEventId = OTHER_FLOW_ID,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancel_reason" to "abandoned"),
        )
    }
    //endregion

    private companion object {
        const val FLOW_ID = 123L
        const val OTHER_FLOW_ID = 456L
        const val TAB_ID = "tab_1"
        const val OTHER_TAB_ID = "tab_2"
        const val CUSTOM_TAB_ID = "${CUSTOM_TAB_NAME_PREFIX}tab_3"
    }
}
