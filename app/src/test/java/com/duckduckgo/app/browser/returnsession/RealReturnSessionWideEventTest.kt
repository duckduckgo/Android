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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RealReturnSessionWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore = mock()

    // MutableSharedFlow, not StateFlow: needs to be able to re-emit the same value (simulating the
    // underlying DataStore re-emitting on an unrelated Preferences write) without built-in dedup.
    private val optionFlow = MutableSharedFlow<ShowOnAppLaunchOption>(replay = 1)
    private val returnSessionWideEventFeature =
        FakeFeatureToggleFactory.create(ReturnSessionWideEventFeature::class.java)

    private lateinit var testee: RealReturnSessionWideEvent

    private val defaultMetadata = mapOf(
        "after_idle" to "false",
        "landed_on" to "ntp_user_initiated",
        "status_reason" to "app_terminated",
    )

    @Before
    fun setup() = runTest {
        optionFlow.tryEmit(ShowOnAppLaunchOption.NewTabPage)
        whenever(showOnAppLaunchOptionDataStore.optionFlow).thenReturn(optionFlow)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)
        returnSessionWideEventFeature.self().setRawStoredState(Toggle.State(true))

        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(123L))
        whenever(wideEventClient.intervalStart(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalEnd(any(), any())).thenReturn(Result.success(100.milliseconds))
        whenever(wideEventClient.flowFinish(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.flowAbort(any())).thenReturn(Result.success(Unit))

        testee = buildTestee()
    }

    // Only tests that need optionFlow seeded to something other than NewTabPage before construction
    // (so that value becomes the collector's dropped baseline) call this to get a fresh instance.
    private fun buildTestee(): RealReturnSessionWideEvent = RealReturnSessionWideEvent(
        wideEventClient = wideEventClient,
        settingsDataStore = settingsDataStore,
        showOnAppLaunchOptionDataStore = showOnAppLaunchOptionDataStore,
        returnSessionWideEventFeature = { returnSessionWideEventFeature },
        dispatchers = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    private fun startSession(
        testee: RealReturnSessionWideEvent = this.testee,
        afterIdle: Boolean = false,
        landing: ReturnSessionLanding = if (afterIdle) ReturnSessionLanding.NTP else ReturnSessionLanding.NTP_USER_INITIATED,
    ) {
        testee.onReturnLandingResolved(ReturnSessionLandingResult(afterIdle, landing))
    }

    @Test
    fun `when ordinary return landing resolves with no selected tab then landed_on is ntp_user_initiated`() = runTest {
        testee.onReturnLandingResolved(
            ReturnSessionLandingResult(
                afterIdle = false,
                landing = ReturnSessionLanding.NTP_USER_INITIATED,
            ),
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(defaultMetadata),
            cleanupPolicy = eq(CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false, flowStatus = FlowStatus.Unknown)),
            samplingProbability = eq(0.05f),
            definition = any(),
        )
    }

    @Test
    fun `when after-idle return landing resolves with no selected tab then landed_on is ntp`() = runTest {
        testee.onReturnLandingResolved(
            ReturnSessionLandingResult(
                afterIdle = true,
                landing = ReturnSessionLanding.NTP,
            ),
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(
                defaultMetadata +
                    mapOf(
                        "after_idle" to "true",
                        "landed_on" to "ntp",
                    ),
            ),
            cleanupPolicy = any(),
            samplingProbability = eq(0.05f),
            definition = any(),
        )
    }

    @Test
    fun `when landing resolves as ntp then emitted landed_on uses the passed destination`() = runTest {
        testee.onReturnLandingResolved(
            ReturnSessionLandingResult(
                afterIdle = true,
                landing = ReturnSessionLanding.NTP,
            ),
        )
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = argThat { this["landed_on"] == "ntp" },
            cleanupPolicy = any(),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun `when after-idle NTP landing resolves then finish metadata reflects after_idle true and landed_on ntp`() = runTest {
        startSession(afterIdle = true)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["after_idle"] == "true" && this["landed_on"] == "ntp" },
        )
    }

    @Test
    fun `when after-idle web landing resolves then finish metadata reflects after_idle true and keeps resolved landed_on`() =
        runTest {
            startSession(afterIdle = true, landing = ReturnSessionLanding.WEB)
            coroutineRule.testScope.testScheduler.advanceUntilIdle()

            testee.onSearchSubmitted()
            coroutineRule.testScope.testScheduler.advanceUntilIdle()

            verify(wideEventClient).flowFinish(
                wideEventId = eq(123L),
                status = eq<FlowStatus>(FlowStatus.Success),
                metadata = argThat { this["after_idle"] == "true" && this["landed_on"] == "web" },
            )
        }

    @Test
    fun `when after-idle Duck AI LUT landing resolves then landed_on reflects the resumed tab`() = runTest {
        startSession(afterIdle = true, landing = ReturnSessionLanding.DUCK_AI)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["after_idle"] == "true" && this["landed_on"] == "duck_ai" },
        )
    }

    @Test
    fun `when resolved landing is duck ai then landed_on is duck_ai`() = runTest {
        startSession(landing = ReturnSessionLanding.DUCK_AI)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(defaultMetadata + ("landed_on" to "duck_ai")),
            cleanupPolicy = any(),
            samplingProbability = eq(0.05f),
            definition = any(),
        )
    }

    @Test
    fun `when resolved landing is serp then landed_on is serp`() = runTest {
        startSession(landing = ReturnSessionLanding.SERP)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(defaultMetadata + ("landed_on" to "serp")),
            cleanupPolicy = any(),
            samplingProbability = eq(0.05f),
            definition = any(),
        )
    }

    @Test
    fun `when resolved landing is regular website then landed_on is web`() = runTest {
        startSession(landing = ReturnSessionLanding.WEB)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(defaultMetadata + ("landed_on" to "web")),
            cleanupPolicy = any(),
            samplingProbability = eq(0.05f),
            definition = any(),
        )
    }

    @Test
    fun `when resolved landing is user initiated ntp then landed_on is ntp_user_initiated`() = runTest {
        startSession(landing = ReturnSessionLanding.NTP_USER_INITIATED)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(defaultMetadata),
            cleanupPolicy = any(),
            samplingProbability = eq(0.05f),
            definition = any(),
        )
    }

    @Test
    fun `when there is no prior background timestamp then time_away_ms_bucketed is omitted`() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(defaultMetadata),
            cleanupPolicy = any(),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun `when backgrounded under a minute ago then time_away_ms_bucketed is 0`() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(System.currentTimeMillis() - 1_000L)

        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(defaultMetadata + ("time_away_ms_bucketed" to "0")),
            cleanupPolicy = any(),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun `when backgrounded exactly a minute ago then time_away_ms_bucketed is 60000`() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(System.currentTimeMillis() - 60_000L)

        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(defaultMetadata + ("time_away_ms_bucketed" to "60000")),
            cleanupPolicy = any(),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun `when backgrounded over an hour ago then time_away_ms_bucketed is 3600000`() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(System.currentTimeMillis() - 3_700_000L)

        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("return_session"),
            flowEntryPoint = isNull(),
            metadata = eq(defaultMetadata + ("time_away_ms_bucketed" to "3600000")),
            cleanupPolicy = any(),
            samplingProbability = eq(0.05f),
            definition = any(),
        )
    }

    @Test
    fun `when onOpen called twice then prior flow is aborted before new flowStart`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))
            .thenReturn(Result.success(2L))

        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowAbort(1L)
    }

    @Test
    fun `when feature flag disabled then no flow operations occur`() = runTest {
        returnSessionWideEventFeature.self().setRawStoredState(Toggle.State(false))

        startSession()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `when feature flag disabled mid-session then active flow is aborted instead of left dangling`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        returnSessionWideEventFeature.self().setRawStoredState(Toggle.State(false))
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowAbort(123L)
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when onSearchSubmitted terminates session then flowFinish is Success with search_submitted reason`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = eq(
                mapOf(
                    "after_idle" to "false",
                    "landed_on" to "ntp_user_initiated",
                    "status_reason" to "search_submitted",
                    "focused" to "false",
                    "page_engaged" to "false",
                    "back_pressed" to "false",
                    "opening_screen_changed" to "false",
                    "close_tab_tapped" to "false",
                    "burn_tab_tapped" to "false",
                ),
            ),
        )
    }

    @Test
    fun `when onUrlSubmitted terminates session then flowFinish reason is url_submitted`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onUrlSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["status_reason"] == "url_submitted" },
        )
    }

    @Test
    fun `when onAiPromptSubmitted terminates session then flowFinish reason is ai_prompt_submitted`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onAiPromptSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["status_reason"] == "ai_prompt_submitted" },
        )
    }

    @Test
    fun `when onChatSelected terminates session then flowFinish is Success with chat_selected reason`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onChatSelected()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["status_reason"] == "chat_selected" },
        )
    }

    @Test
    fun `when onFavoriteSelected terminates session then flowFinish is Success with favorite_selected reason`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onFavoriteSelected()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["status_reason"] == "favorite_selected" },
        )
    }

    @Test
    fun `when onReturnToPageTapped terminates session then flowFinish is Success with return_to_page_tapped reason`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onReturnToPageTapped()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["status_reason"] == "return_to_page_tapped" },
        )
    }

    @Test
    fun `when onTabSwitcherSelected terminates session then flowFinish is Success with tab_switcher_selected reason`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onTabSwitcherSelected()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["status_reason"] == "tab_switcher_selected" },
        )
    }

    @Test
    fun `when successful terminal occurs before another interaction then time to first interaction ends`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(123L, "time_to_first_interaction_ms_bucketed")
    }

    @Test
    fun `when return closes before any interaction then time to first interaction does not end`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onReturnClosed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(123L, "time_to_first_interaction_ms_bucketed")
    }

    @Test
    fun `when return closes after page interaction then time to first interaction does not end again`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onWebViewEngaged()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onReturnClosed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, times(1)).intervalEnd(123L, "time_to_first_interaction_ms_bucketed")
    }

    @Test
    fun `when return closes before any interaction then session duration still ends`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onReturnClosed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).intervalEnd(123L, "session_duration_ms_bucketed")
    }

    @Test
    fun `when return closes with active session then flowFinish is Cancelled with app_backgrounded reason`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onReturnClosed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Cancelled),
            metadata = argThat { this["status_reason"] == "app_backgrounded" },
        )
    }

    @Test
    fun `when return closes without active session then no flowFinish occurs`() = runTest {
        testee.onReturnClosed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when return closes before landing resolves then pending focus is cleared`() = runTest {
        testee.onLandingFocusCaptured(focused = true)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onReturnClosed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["focused"] == "false" },
        )
    }

    @Test
    fun `when landing input is initially focused then focused metadata is true on terminal`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onLandingFocusCaptured(focused = true)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["focused"] == "true" },
        )
    }

    @Test
    fun `when landing focus is captured before landing resolves then focused metadata is true on terminal`() = runTest {
        testee.onLandingFocusCaptured(focused = true)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["focused"] == "true" },
        )
    }

    @Test
    fun `when landing focus is captured while session start is suspended then focused metadata is true on terminal`() = runTest {
        val flowStartResult = CompletableDeferred<Result<Long>>()
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .doSuspendableAnswer { flowStartResult.await() }

        startSession()
        coroutineRule.testScope.testScheduler.runCurrent()

        testee.onLandingFocusCaptured(focused = true)
        coroutineRule.testScope.testScheduler.runCurrent()

        flowStartResult.complete(Result.success(123L))
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["focused"] == "true" },
        )
    }

    @Test
    fun `when landing input is initially unfocused then later focus does not change focused metadata`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onLandingFocusCaptured(focused = false)
        testee.onLandingFocusCaptured(focused = true)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["focused"] == "false" },
        )
    }

    @Test
    fun `when optionFlow re-emits the identical option then opening_screen_changed stays false`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        optionFlow.tryEmit(ShowOnAppLaunchOption.NewTabPage)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["opening_screen_changed"] == "false" },
        )
    }

    @Test
    fun `when a Specific Page resolvedUrl updates then opening_screen_changed stays false`() = runTest {
        // Reproduces the real trigger: setResolvedPageUrl() re-emits a *different* SpecificPage
        // instance (resolvedUrl is part of its equals()) as the destination loads, even though the
        // user's configured option (the url) never changed. Drain setup()'s NewTabPage seed first
        // (via the @Before testee's own collector), then seed SpecificPage and rebuild testee, so
        // *that* value — not NewTabPage — is what the new collector drops as its initial baseline;
        // otherwise the NewTabPage-to-SpecificPage transition itself would be the (correctly)
        // recorded change, masking the thing under test.
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        optionFlow.tryEmit(ShowOnAppLaunchOption.SpecificPage(url = "https://example.com", resolvedUrl = null))
        val testee = buildTestee()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        startSession(testee = testee)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        optionFlow.tryEmit(ShowOnAppLaunchOption.SpecificPage(url = "https://example.com", resolvedUrl = "https://example.com/resolved"))
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["opening_screen_changed"] == "false" },
        )
    }

    @Test
    fun `when optionFlow emits a genuinely different option then opening_screen_changed is true`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        optionFlow.tryEmit(ShowOnAppLaunchOption.LastOpenedTab)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["opening_screen_changed"] == "true" },
        )
    }

    @Test
    fun `when a Specific Page url genuinely changes then opening_screen_changed is true`() = runTest {
        // Same baseline-isolation reasoning as the resolvedUrl test above.
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        optionFlow.tryEmit(ShowOnAppLaunchOption.SpecificPage(url = "https://example.com", resolvedUrl = null))
        val testee = buildTestee()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        startSession(testee = testee)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        optionFlow.tryEmit(ShowOnAppLaunchOption.SpecificPage(url = "https://other.com", resolvedUrl = null))
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["opening_screen_changed"] == "true" },
        )
    }

    @Test
    fun `when onCloseTabTapped recorded then close_tab_tapped metadata is true on terminal`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onCloseTabTapped()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["close_tab_tapped"] == "true" },
        )
    }

    @Test
    fun `when onBurnTabTapped recorded then burn_tab_tapped metadata is true on terminal`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onBurnTabTapped()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["burn_tab_tapped"] == "true" },
        )
    }

    @Test
    fun `when onBackPressed recorded without active session then is a no-op`() = runTest {
        testee.onBackPressed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when onBackPressed recorded with active session then back_pressed metadata is true on terminal`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onBackPressed()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(123L),
            status = eq<FlowStatus>(FlowStatus.Success),
            metadata = argThat { this["back_pressed"] == "true" },
        )
    }

    @Test
    fun `when terminal already fired then subsequent terminal calls produce only one finish`() = runTest {
        startSession()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        testee.onSearchSubmitted()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        testee.onTabSwitcherSelected()
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(wideEventClient, times(1)).flowFinish(any(), any(), any())
    }
}
