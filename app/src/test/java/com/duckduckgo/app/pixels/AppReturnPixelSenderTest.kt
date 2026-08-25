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

package com.duckduckgo.app.pixels

import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.generalsettings.showonapplaunch.IdleThresholdResolver
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AppReturnPixelSenderTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val idleThresholdResolver: IdleThresholdResolver = mock()
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore = mock()
    private val duckChatInputModeState: DuckChatInputModeState = mock()
    private val duckAiFeatureState: DuckAiFeatureState = mock()
    private val androidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val unsentForgetAllPixelStore: UnsentForgetAllPixelStore = mock()

    private lateinit var testee: RealAppReturnPixelSender

    @Before
    fun setup() {
        whenever(idleThresholdResolver.effectiveThresholdSeconds(anyOrNull())).thenReturn(1800L)
        whenever(showOnAppLaunchOptionDataStore.optionFlow).thenReturn(flowOf(ShowOnAppLaunchOption.NewTabPage))
        whenever(duckChatInputModeState.inputModeCapability).thenReturn(MutableStateFlow(NativeInputState.InputMode.SEARCH_ONLY))
        whenever(duckAiFeatureState.nativeInputFieldEnabled).thenReturn(MutableStateFlow(false))
        whenever(unsentForgetAllPixelStore.lastClearTimestamp).thenReturn(0L)
        androidBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(State(enable = false))

        testee = RealAppReturnPixelSender(
            pixel = { pixel },
            settingsDataStore = settingsDataStore,
            idleThresholdResolver = idleThresholdResolver,
            showOnAppLaunchOptionDataStore = showOnAppLaunchOptionDataStore,
            duckChatInputModeState = duckChatInputModeState,
            duckAiFeatureState = duckAiFeatureState,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
            unsentForgetAllPixelStore = unsentForgetAllPixelStore,
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun whenColdStartThenParamsCarryColdStartBucket() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        testee.fireIfNeeded("standard")
        advanceUntilIdle()

        val params = mapOf(
            AppReturnPixelParameters.TIME_AWAY_BUCKET to "cold_start",
            AppReturnPixelParameters.EXCEEDED_IDLE_THRESHOLD to "false",
            AppReturnPixelParameters.IDLE_THRESHOLD_SECONDS to "1800",
            AppReturnPixelParameters.AFTER_INACTIVITY_OPTION to "new_tab_page",
            AppReturnPixelParameters.FEATURE_ELIGIBLE to "false",
            AppReturnPixelParameters.UNIFIED_INPUT_AVAILABLE to "false",
            AppReturnPixelParameters.TOGGLE_VISIBLE to "false",
            AppReturnPixelParameters.LAUNCH_SOURCE to "standard",
            AppReturnPixelParameters.PETAL to "randomize",
        )
        verify(pixel).fire(pixel = AppPixelName.APP_RETURN_COUNT, parameters = params)
        verify(pixel).fire(pixel = AppPixelName.APP_RETURN_DAILY, parameters = params, type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenBackgroundedUnderAMinuteAgoThenParamsCarryLt1mBucket() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(System.currentTimeMillis() - 1_000L)

        testee.fireIfNeeded("standard")
        advanceUntilIdle()

        verify(pixel).fire(
            pixel = eq(AppPixelName.APP_RETURN_COUNT),
            parameters = argThat { this[AppReturnPixelParameters.TIME_AWAY_BUCKET] == "lt_1m" },
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenBackgroundedOverAnHourAgoThenParamsCarryGt60mBucketAndExceededThreshold() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(System.currentTimeMillis() - 3_700_000L)

        testee.fireIfNeeded("standard")
        advanceUntilIdle()

        verify(pixel).fire(
            pixel = eq(AppPixelName.APP_RETURN_COUNT),
            parameters = argThat {
                this[AppReturnPixelParameters.TIME_AWAY_BUCKET] == "gt_60m" &&
                    this[AppReturnPixelParameters.EXCEEDED_IDLE_THRESHOLD] == "true"
            },
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenToggleCapabilityIsSearchAndDuckAiThenParamsCarryToggleVisibleTrue() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)
        whenever(duckChatInputModeState.inputModeCapability)
            .thenReturn(MutableStateFlow(NativeInputState.InputMode.SEARCH_AND_DUCK_AI))
        whenever(duckAiFeatureState.nativeInputFieldEnabled).thenReturn(MutableStateFlow(true))

        testee.fireIfNeeded("standard")
        advanceUntilIdle()

        verify(pixel).fire(
            pixel = eq(AppPixelName.APP_RETURN_COUNT),
            parameters = argThat {
                this[AppReturnPixelParameters.TOGGLE_VISIBLE] == "true" &&
                    this[AppReturnPixelParameters.UNIFIED_INPUT_AVAILABLE] == "true"
            },
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenLaunchSourceGivenThenParamsCarryIt() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        testee.fireIfNeeded("widget")
        advanceUntilIdle()

        verify(pixel).fire(
            pixel = eq(AppPixelName.APP_RETURN_COUNT),
            parameters = argThat { this[AppReturnPixelParameters.LAUNCH_SOURCE] == "widget" },
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenLaunchedByFireActionThenDoNotFire() = runTest {
        whenever(unsentForgetAllPixelStore.lastClearTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireIfNeeded("standard")
        advanceUntilIdle()

        verify(pixel, never()).fire(eq(AppPixelName.APP_RETURN_COUNT), any<Map<String, String>>(), any(), any())
        verify(pixel, never()).fire(eq(AppPixelName.APP_RETURN_DAILY), any<Map<String, String>>(), any(), eq(Pixel.PixelType.Daily()))
    }

    @Test
    fun whenLaunchedByFireActionThenLaterFireIfNeededInSameSessionIsSuppressedEvenAfterGraceWindow() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)
        whenever(unsentForgetAllPixelStore.lastClearTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireIfNeeded("standard")
        advanceUntilIdle()

        // grace window has since lapsed, but this is still the same foreground session
        whenever(unsentForgetAllPixelStore.lastClearTimestamp).thenReturn(0L)
        testee.fireIfNeeded("standard")
        advanceUntilIdle()

        verify(pixel, never()).fire(eq(AppPixelName.APP_RETURN_COUNT), any<Map<String, String>>(), any(), any())
    }

    @Test
    fun whenFireIfNeededCalledTwiceThenOnlyFiresOnce() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        testee.fireIfNeeded("standard")
        testee.fireIfNeeded("widget")
        advanceUntilIdle()

        verify(pixel).fire(pixel = eq(AppPixelName.APP_RETURN_COUNT), parameters = any(), encodedParameters = any(), type = any())
    }

    @Test
    fun whenStoppedThenNextFireIfNeededFiresAgain() = runTest {
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        testee.fireIfNeeded("standard")
        testee.onStop(mock())
        testee.fireIfNeeded("widget")
        advanceUntilIdle()

        verify(pixel, times(2)).fire(pixel = eq(AppPixelName.APP_RETURN_COUNT), parameters = any(), encodedParameters = any(), type = any())
    }
}
