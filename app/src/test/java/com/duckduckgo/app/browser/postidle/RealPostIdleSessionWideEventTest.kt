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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.InputMode
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RealPostIdleSessionWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val displayedModeFlow = MutableStateFlow(InputMode.SEARCH)
    private val duckChatInputModeState: DuckChatInputModeState = mock()
    private val androidBrowserConfigFeature =
        FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private lateinit var testee: RealPostIdleSessionWideEvent

    @Before
    fun setup() = runTest {
        whenever(duckChatInputModeState.displayedMode).thenReturn(displayedModeFlow)
        androidBrowserConfigFeature.sendPostIdleSessionWideEvent().setRawStoredState(Toggle.State(true))

        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any())).thenReturn(Result.success(123L))
        whenever(wideEventClient.flowStep(any(), any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalStart(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalEnd(any(), any())).thenReturn(Result.success(100.milliseconds))
        whenever(wideEventClient.flowFinish(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.flowAbort(any())).thenReturn(Result.success(Unit))

        testee = RealPostIdleSessionWideEvent(
            wideEventClient = wideEventClient,
            duckChatInputModeState = duckChatInputModeState,
            androidBrowserConfigFeature = { androidBrowserConfigFeature },
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when onSurfaceShown then starts flow with surface metadata and starts intervals`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("post_idle_session"),
            flowEntryPoint = isNull(),
            metadata = eq(mapOf("surface" to "ntp")),
            cleanupPolicy = eq(
                CleanupPolicy.OnProcessStart(
                    ignoreIfIntervalTimeoutPresent = false,
                    flowStatus = FlowStatus.Unknown,
                ),
            ),
        )
        verify(wideEventClient).intervalStart(eq(123L), eq("session_duration_ms_bucketed"), anyOrNull(), anyOrNull())
        verify(wideEventClient).intervalStart(eq(123L), eq("time_to_first_interaction_ms_bucketed"), anyOrNull(), anyOrNull())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }

    @Test
    fun `when onSurfaceShown called twice then prior flow is aborted before new flowStart`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(1L))
            .thenReturn(Result.success(2L))

        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onLutShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowAbort(1L)
        verify(wideEventClient).flowStart(
            name = eq("post_idle_session"),
            flowEntryPoint = isNull(),
            metadata = eq(mapOf("surface" to "lut")),
            cleanupPolicy = any(),
        )
    }

    @Test
    fun `when feature flag disabled then no flow operations occur`() = runTest {
        androidBrowserConfigFeature.sendPostIdleSessionWideEvent().setRawStoredState(Toggle.State(false))

        testee.onHatchShownAfterIdle()
        testee.onBarUsed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `when onPageEngaged called twice then ttfi end fires only once and metadata reflects engagement`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onNtpEngaged()
        testee.onNtpEngaged()
        testee.onBarUsed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(123L, "time_to_first_interaction_ms_bucketed")
        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = eq(
                mapOf(
                    "surface" to "ntp",
                    "status_reason" to "bar_used",
                    "page_engaged" to "true",
                    "toggle_used" to "false",
                    "back_pressed" to "false",
                ),
            ),
        )
    }

    @Test
    fun `when onPageEngaged called without active session then is a no-op`() = runTest {
        testee.onNtpEngaged()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when onBackPressed called multiple times then ttfi end fires only once and metadata reflects back press`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onBackPressed()
        testee.onBackPressed()
        testee.onBarUsed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(123L, "time_to_first_interaction_ms_bucketed")
        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = eq(
                mapOf(
                    "surface" to "ntp",
                    "status_reason" to "bar_used",
                    "page_engaged" to "false",
                    "toggle_used" to "false",
                    "back_pressed" to "true",
                ),
            ),
        )
    }

    @Test
    fun `when displayedMode changes during active session then toggle_used metadata is set on terminal`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        displayedModeFlow.value = InputMode.DUCK_AI
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onBarUsed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = eq(
                mapOf(
                    "surface" to "ntp",
                    "status_reason" to "bar_used",
                    "page_engaged" to "false",
                    "toggle_used" to "true",
                    "back_pressed" to "false",
                ),
            ),
        )
    }

    @Test
    fun `when displayedMode changes without active session then nothing happens`() = runTest {
        displayedModeFlow.value = InputMode.DUCK_AI
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when onBarUsed terminates session then flowFinish is Success with bar_used reason`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onBarUsed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(123L, "session_duration_ms_bucketed")
        verify(wideEventClient).intervalEnd(123L, "time_to_first_interaction_ms_bucketed")
        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = eq(
                mapOf(
                    "surface" to "ntp",
                    "status_reason" to "bar_used",
                    "page_engaged" to "false",
                    "toggle_used" to "false",
                    "back_pressed" to "false",
                ),
            ),
        )
    }

    @Test
    fun `when onReturnToPageTapped terminates session then flowFinish reason is return_to_page_tapped`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onReturnToPageTapped()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = eq(
                mapOf(
                    "surface" to "ntp",
                    "status_reason" to "return_to_page_tapped",
                    "page_engaged" to "false",
                    "toggle_used" to "false",
                    "back_pressed" to "false",
                ),
            ),
        )
    }

    @Test
    fun `when onTabSwitcherSelected terminates session then flowFinish reason is tab_switcher_selected`() = runTest {
        testee.onLutShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onTabSwitcherSelected()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = eq(
                mapOf(
                    "surface" to "lut",
                    "status_reason" to "tab_switcher_selected",
                    "page_engaged" to "false",
                    "toggle_used" to "false",
                    "back_pressed" to "false",
                ),
            ),
        )
    }

    @Test
    fun `when onFavoriteSelected terminates session then flowFinish reason is favorite_selected`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onFavoriteSelected()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = eq(
                mapOf(
                    "surface" to "ntp",
                    "status_reason" to "favorite_selected",
                    "page_engaged" to "false",
                    "toggle_used" to "false",
                    "back_pressed" to "false",
                ),
            ),
        )
    }

    @Test
    fun `when onClose called with active session then flowFinish is Cancelled with app_backgrounded reason`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onClose()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Cancelled),
            metadata = eq(
                mapOf(
                    "surface" to "ntp",
                    "status_reason" to "app_backgrounded",
                    "page_engaged" to "false",
                    "toggle_used" to "false",
                    "back_pressed" to "false",
                ),
            ),
        )
    }

    @Test
    fun `when onClose called without active session then no flowFinish occurs`() = runTest {
        testee.onClose()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when all non-terminal flags set then flowFinish metadata reflects them`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onNtpEngaged()
        displayedModeFlow.value = InputMode.DUCK_AI
        testee.onBackPressed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onBarUsed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = eq(
                mapOf(
                    "surface" to "ntp",
                    "status_reason" to "bar_used",
                    "page_engaged" to "true",
                    "toggle_used" to "true",
                    "back_pressed" to "true",
                ),
            ),
        )
    }

    @Test
    fun `when terminal already fired then subsequent terminal calls are no-ops`() = runTest {
        testee.onHatchShownAfterIdle()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onBarUsed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onTabSwitcherSelected()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(any(), any(), any())
    }
}
