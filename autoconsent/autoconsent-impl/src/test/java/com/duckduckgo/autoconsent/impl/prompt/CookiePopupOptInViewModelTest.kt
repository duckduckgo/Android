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

package com.duckduckgo.autoconsent.impl.prompt

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.impl.FakeSettingsRepository
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelParameters
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Command
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Variant
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class CookiePopupOptInViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val autoconsent: Autoconsent = mock()
    private val feature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)
    private val settingsRepository = FakeSettingsRepository()
    private val now = System.currentTimeMillis()
    private val currentTimeProvider: CurrentTimeProvider = mock {
        on { currentTimeMillis() } doReturn now
    }
    private val pixel: Pixel = mock()

    private val testee by lazy {
        CookiePopupOptInViewModel(
            autoconsent = autoconsent,
            settingsRepository = settingsRepository,
            dispatchers = coroutineRule.testDispatcherProvider,
            currentTimeProvider = currentTimeProvider,
            pixel = pixel,
            autoconsentFeature = feature,
        )
    }

    @Test
    fun whenProtectionAlreadyEnabledThenProtectionOnVariant() {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)

        assertEquals(Variant.PROTECTION_ON, testee.viewState.value.variant)
    }

    @Test
    fun whenProtectionDisabledThenProtectionOffVariant() {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)

        assertEquals(Variant.PROTECTION_OFF, testee.viewState.value.variant)
    }

    @Test
    fun whenCloseButtonFlagEnabledThenCloseButtonIsVisible() {
        feature.cookiePopUpOptInPromptCloseButton().setRawStoredState(Toggle.State(enable = true))

        assertTrue(testee.viewState.value.isCloseButtonVisible)
    }

    @Test
    fun whenCloseButtonFlagDisabledThenCloseButtonIsHidden() {
        feature.cookiePopUpOptInPromptCloseButton().setRawStoredState(Toggle.State(enable = false))

        assertFalse(testee.viewState.value.isCloseButtonVisible)
    }

    @Test
    fun whenBackNavigationFlagEnabledThenBackNavigationIsEnabled() {
        feature.cookiePopUpOptInPromptDismissible().setRawStoredState(Toggle.State(enable = true))

        assertTrue(testee.viewState.value.isBackNavigationEnabled)
    }

    @Test
    fun whenBackNavigationFlagDisabledThenBackNavigationIsDisabled() {
        feature.cookiePopUpOptInPromptDismissible().setRawStoredState(Toggle.State(enable = false))

        assertFalse(testee.viewState.value.isBackNavigationEnabled)
    }

    @Test
    fun whenCloseButtonClickedThenCloseCommandEmitted() = runTest {
        testee.commands().test {
            testee.onCloseClicked()

            assertEquals(Command.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAcceptedWithProtectionAlreadyEnabledThenOnlyClickAcceptEnabled() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)

        testee.onAcceptClicked()

        verify(autoconsent).changeClickAcceptEnabled(true)
        verify(autoconsent, never()).changeSetting(true)
    }

    @Test
    fun whenAcceptedWithProtectionDisabledThenProtectionAndClickAcceptEnabled() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)

        testee.onAcceptClicked()

        verify(autoconsent).changeSetting(true)
        verify(autoconsent).changeClickAcceptEnabled(true)
    }

    @Test
    fun whenAcceptedThenCloseCommandEmitted() = runTest {
        testee.commands().test {
            testee.onAcceptClicked()

            assertEquals(Command.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeclinedThenNoSettingChanged() = runTest {
        testee.onDeclineClicked()

        verify(autoconsent, never()).changeSetting(true)
        verify(autoconsent, never()).changeClickAcceptEnabled(true)
    }

    @Test
    fun whenDeclinedThenCloseCommandEmitted() = runTest {
        testee.commands().test {
            testee.onDeclineClicked()

            assertEquals(Command.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAcceptedThenChoiceRecorded() = runTest {
        testee.onAcceptClicked()

        assertTrue(settingsRepository.optInPromptChoiceMade)
    }

    @Test
    fun whenDeclinedThenChoiceRecorded() = runTest {
        testee.onDeclineClicked()

        assertTrue(settingsRepository.optInPromptChoiceMade)
    }

    @Test
    fun whenNoChoiceMadeYetThenNotRecorded() {
        assertFalse(settingsRepository.optInPromptChoiceMade)
    }

    @Test
    fun whenPromptShownThenShownCountIncremented() = runTest {
        testee.onPromptShown()

        assertEquals(1, settingsRepository.optInPromptShownCount)
    }

    @Test
    fun whenPromptShownAgainThenShownCountIncrementedFromTheStoredValue() = runTest {
        settingsRepository.optInPromptShownCount = 2

        testee.onPromptShown()

        assertEquals(3, settingsRepository.optInPromptShownCount)
    }

    @Test
    fun whenPromptNotShownThenShownCountNotIncremented() = runTest {
        testee.onDeclineClicked()

        assertEquals(0, settingsRepository.optInPromptShownCount)
    }

    @Test
    fun whenAcceptedThenConfirmedPixelReportsMaxPreference() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)
        settingsRepository.optInPromptFirstShownAt = now - TimeUnit.SECONDS.toMillis(30)

        testee.onAcceptClicked()

        verify(pixel).enqueueFire(
            AutoConsentPixel.COOKIE_POPUP_OPT_IN_OPTION_CONFIRMED,
            mapOf(
                AutoconsentPixelParameters.AUTOCONSENT_ENABLED to "false",
                AutoconsentPixelParameters.COOKIE_POPUP_PREFERENCE to "max",
                AutoconsentPixelParameters.TIME_SINCE_SHOWN to "0-1min",
            ),
        )
    }

    @Test
    fun whenDeclinedWithProtectionOffThenConfirmedPixelReportsOffPreference() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)
        settingsRepository.optInPromptFirstShownAt = now - TimeUnit.MINUTES.toMillis(3)

        testee.onDeclineClicked()

        verify(pixel).enqueueFire(
            AutoConsentPixel.COOKIE_POPUP_OPT_IN_OPTION_CONFIRMED,
            mapOf(
                AutoconsentPixelParameters.AUTOCONSENT_ENABLED to "false",
                AutoconsentPixelParameters.COOKIE_POPUP_PREFERENCE to "off",
                AutoconsentPixelParameters.TIME_SINCE_SHOWN to "1-5min",
            ),
        )
    }

    @Test
    fun whenDeclinedWithProtectionOnThenConfirmedPixelReportsDefaultPreference() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)
        settingsRepository.optInPromptFirstShownAt = now - TimeUnit.MINUTES.toMillis(30)

        testee.onDeclineClicked()

        verify(pixel).enqueueFire(
            AutoConsentPixel.COOKIE_POPUP_OPT_IN_OPTION_CONFIRMED,
            mapOf(
                AutoconsentPixelParameters.AUTOCONSENT_ENABLED to "true",
                AutoconsentPixelParameters.COOKIE_POPUP_PREFERENCE to "default",
                AutoconsentPixelParameters.TIME_SINCE_SHOWN to "5-60min",
            ),
        )
    }

    @Test
    fun whenConfirmedHoursAfterBeingShownThenTimeSinceShownBucketed() = runTest {
        settingsRepository.optInPromptFirstShownAt = now - TimeUnit.HOURS.toMillis(5)

        testee.onDeclineClicked()

        verify(pixel).enqueueFire(
            AutoConsentPixel.COOKIE_POPUP_OPT_IN_OPTION_CONFIRMED,
            mapOf(
                AutoconsentPixelParameters.AUTOCONSENT_ENABLED to "false",
                AutoconsentPixelParameters.COOKIE_POPUP_PREFERENCE to "off",
                AutoconsentPixelParameters.TIME_SINCE_SHOWN to "1h-1d",
            ),
        )
    }

    @Test
    fun whenConfirmedDaysAfterBeingShownThenTimeSinceShownBucketed() = runTest {
        settingsRepository.optInPromptFirstShownAt = now - TimeUnit.DAYS.toMillis(2)

        testee.onDeclineClicked()

        verify(pixel).enqueueFire(
            AutoConsentPixel.COOKIE_POPUP_OPT_IN_OPTION_CONFIRMED,
            mapOf(
                AutoconsentPixelParameters.AUTOCONSENT_ENABLED to "false",
                AutoconsentPixelParameters.COOKIE_POPUP_PREFERENCE to "off",
                AutoconsentPixelParameters.TIME_SINCE_SHOWN to "1d+",
            ),
        )
    }

    @Test
    fun whenPromptShownForTheFirstTimeThenFirstShownPixelFired() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)

        testee.onPromptShown()

        verify(pixel).enqueueFire(
            AutoConsentPixel.COOKIE_POPUP_OPT_IN_SHOWN_FIRST,
            mapOf(AutoconsentPixelParameters.AUTOCONSENT_ENABLED to "false"),
        )
    }

    @Test
    fun whenPromptShownAgainThenRepeatShownPixelFired() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)
        settingsRepository.optInPromptFirstShownAt = now - TimeUnit.DAYS.toMillis(1)

        testee.onPromptShown()

        verify(pixel).enqueueFire(
            AutoConsentPixel.COOKIE_POPUP_OPT_IN_SHOWN_REPEAT,
            mapOf(AutoconsentPixelParameters.AUTOCONSENT_ENABLED to "true"),
        )
    }

    @Test
    fun whenPromptShownForTheFirstTimeThenFirstShownTimestampRecorded() = runTest {
        testee.onPromptShown()

        assertEquals(now, settingsRepository.optInPromptFirstShownAt)
    }

    @Test
    fun whenPromptShownAgainThenFirstShownTimestampNotOverwritten() = runTest {
        settingsRepository.optInPromptFirstShownAt = now - TimeUnit.DAYS.toMillis(1)

        testee.onPromptShown()

        assertEquals(now - TimeUnit.DAYS.toMillis(1), settingsRepository.optInPromptFirstShownAt)
    }

    @Test
    fun whenConfirmedThenTimeSinceShownMeasuredFromTheFirstDisplay() = runTest {
        testee.onPromptShown()

        testee.onDeclineClicked()

        verify(pixel).enqueueFire(
            AutoConsentPixel.COOKIE_POPUP_OPT_IN_OPTION_CONFIRMED,
            mapOf(
                AutoconsentPixelParameters.AUTOCONSENT_ENABLED to "false",
                AutoconsentPixelParameters.COOKIE_POPUP_PREFERENCE to "off",
                AutoconsentPixelParameters.TIME_SINCE_SHOWN to "0-1min",
            ),
        )
    }
}
