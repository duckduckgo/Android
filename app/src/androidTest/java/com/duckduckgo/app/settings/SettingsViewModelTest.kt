/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_NONE
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_ONLY
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.store.ThemingDataStore
import com.nhaarman.mockitokotlin2.*
import kotlinx.android.synthetic.main.content_settings_general.view.*
import kotlinx.android.synthetic.main.settings_automatically_clear_what_fragment.view.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
@ExperimentalTime
class SettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: SettingsViewModel

    private lateinit var context: Context

    @Mock
    private lateinit var mockThemeSettingsDataStore: ThemingDataStore

    @Mock
    private lateinit var mockAppSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockDefaultBrowserDetector: DefaultBrowserDetector

    @Mock
    private lateinit var mockVariantManager: VariantManager

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockFireAnimationLoader: FireAnimationLoader

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        context = InstrumentationRegistry.getInstrumentation().targetContext

        testee = SettingsViewModel(mockThemeSettingsDataStore, mockAppSettingsDataStore, mockDefaultBrowserDetector, mockVariantManager, mockFireAnimationLoader, mockPixel)

        whenever(mockAppSettingsDataStore.automaticallyClearWhenOption).thenReturn(APP_EXIT_ONLY)
        whenever(mockAppSettingsDataStore.automaticallyClearWhatOption).thenReturn(CLEAR_NONE)
        whenever(mockAppSettingsDataStore.appIcon).thenReturn(AppIcon.DEFAULT)
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)

        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.DEFAULT_VARIANT)
    }

    @Test
    fun whenViewModelInitialisedThenPixelIsFired() {
        testee // init
        verify(mockPixel).fire(AppPixelName.SETTINGS_OPENED)
    }

    @Test
    fun whenStartNotCalledYetThenViewStateInitialisedDefaultValues() = coroutineTestRule.runBlocking {
        testee.viewState().test {
            val value = expectItem()
            assertTrue(value.loading)
            assertEquals("", value.version)
            assertTrue(value.autoCompleteSuggestionsEnabled)
            assertFalse(value.showDefaultBrowserSetting)
            assertFalse(value.isAppDefaultBrowser)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartCalledThenLoadingSetToFalse() = coroutineTestRule.runBlocking {
        testee.start()
        testee.viewState().test {
            val value = expectItem()
            assertEquals(false, value.loading)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartCalledThenVersionSetCorrectly() = coroutineTestRule.runBlocking {
        testee.start()
        testee.viewState().test {

            val value = expectItem()
            val expectedStartString = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            assertTrue(value.version.startsWith(expectedStartString))

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLightThemeToggledOnThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.onLightThemeToggled(true)
            verify(mockThemeSettingsDataStore).theme = DuckDuckGoTheme.LIGHT

            assertEquals(Command.UpdateTheme, expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLightThemeToggledOnThenLighThemePixelIsSent() {
        testee.onLightThemeToggled(true)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT)
    }

    @Test
    fun whenLightThemeTogglesOffThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.onLightThemeToggled(false)
            verify(mockThemeSettingsDataStore).theme = DuckDuckGoTheme.DARK

            assertEquals(Command.UpdateTheme, expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLightThemeToggledOffThenDarkThemePixelIsSent() {
        testee.onLightThemeToggled(false)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_DARK)
    }

    @Test
    fun whenAutocompleteSwitchedOnThenDataStoreIsUpdated() {
        testee.onAutocompleteSettingChanged(true)
        verify(mockAppSettingsDataStore).autoCompleteSuggestionsEnabled = true
    }

    @Test
    fun whenAutocompleteSwitchedOffThenDataStoreIsUpdated() {
        testee.onAutocompleteSettingChanged(false)
        verify(mockAppSettingsDataStore).autoCompleteSuggestionsEnabled = false
    }

    @Test
    fun whenAppLinksSwitchedOnThenDataStoreIsUpdated() {
        testee.onAppLinksSettingChanged(true)
        verify(mockAppSettingsDataStore).appLinksEnabled = true
    }

    @Test
    fun whenAppLinksSwitchedOffThenDataStoreIsUpdated() {
        testee.onAppLinksSettingChanged(false)
        verify(mockAppSettingsDataStore).appLinksEnabled = false
    }

    @Test
    fun whenLeaveFeedBackRequestedThenCommandIsLaunchFeedback() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.userRequestedToSendFeedback()

            assertEquals(Command.LaunchFeedback, expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDefaultBrowserAppAlreadySetToOursThenIsDefaultBrowserFlagIsTrue() = coroutineTestRule.runBlocking {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        testee.start()

        testee.viewState().test {
            assertTrue(expectItem().isAppDefaultBrowser)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDefaultBrowserAppNotSetToOursThenIsDefaultBrowserFlagIsFalse() = coroutineTestRule.runBlocking {
        testee.viewState().test {
            whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
            testee.start()

            assertFalse(expectItem().isAppDefaultBrowser)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCannotBeSetThenFlagToShowSettingIsFalse() = coroutineTestRule.runBlocking {
        testee.viewState().test {
            whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
            testee.start()

            assertFalse(expectItem().showDefaultBrowserSetting)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCanBeSetThenFlagToShowSettingIsTrue() = coroutineTestRule.runBlocking {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
        testee.start()
        testee.viewState().test {
            assertTrue(expectItem().showDefaultBrowserSetting)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenWhitelistSelectedThenPixelIsSentAndWhitelistLaunched() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.onManageWhitelistSelected()

            verify(mockPixel).fire(AppPixelName.SETTINGS_MANAGE_WHITELIST)
            assertEquals(Command.LaunchWhitelist, expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVariantIsEmptyThenEmptyVariantIncludedInSettings() = coroutineTestRule.runBlocking {
        testee.start()
        testee.viewState().test {
            val expectedStartString = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            assertEquals(expectedStartString, expectItem().version)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVariantIsSetThenVariantKeyIncludedInSettings() = coroutineTestRule.runBlocking {
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("ab", filterBy = { true }))
        testee.start()

        testee.viewState().test {
            val expectedStartString = "${BuildConfig.VERSION_NAME} ab (${BuildConfig.VERSION_CODE})"
            assertEquals(expectedStartString, expectItem().version)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenChangeIconRequestedThenCommandIsChangeIcon() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.userRequestedToChangeIcon()

            assertEquals(Command.LaunchAppIcon, expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFireAnimationSettingClickedThenCommandIsLaunchFireAnimationSettings() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.userRequestedToChangeFireAnimation()

            assertEquals(Command.LaunchFireAnimationSettings(FireAnimation.HeroFire), expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFireAnimationSettingClickedThenPixelSent() {
        testee.userRequestedToChangeFireAnimation()

        verify(mockPixel).fire(AppPixelName.FIRE_ANIMATION_SETTINGS_OPENED)
    }

    @Test
    fun whenNewFireAnimationSelectedThenUpdateViewState() = coroutineTestRule.runBlocking {
        val expectedAnimation = FireAnimation.HeroAbstract
        testee.onFireAnimationSelected(expectedAnimation)

        testee.viewState().test {
            assertEquals(expectedAnimation, expectItem().selectedFireAnimation)

            cancelAndConsumeRemainingEvents()
        }

    }

    @Test
    fun whenNewFireAnimationSelectedThenStoreNewSelectedAnimation() {
        testee.onFireAnimationSelected(FireAnimation.HeroWater)

        verify(mockAppSettingsDataStore).selectedFireAnimation = FireAnimation.HeroWater
    }

    @Test
    fun whenNewFireAnimationSelectedThenPreLoadAnimation() {
        testee.onFireAnimationSelected(FireAnimation.HeroWater)

        verify(mockFireAnimationLoader).preloadSelectedAnimation()
    }

    @Test
    fun whenNewFireAnimationSelectedThenPixelSent() {
        testee.onFireAnimationSelected(FireAnimation.HeroWater)

        verify(mockPixel).fire(
            AppPixelName.FIRE_ANIMATION_NEW_SELECTED,
            mapOf(Pixel.PixelParameter.FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_WHIRLPOOL)
        )
    }

    @Test
    fun whenSameFireAnimationSelectedThenDoNotSendPixel() {
        givenSelectedFireAnimation(FireAnimation.HeroFire)

        testee.onFireAnimationSelected(FireAnimation.HeroFire)

        verify(mockPixel, times(0)).fire(
            AppPixelName.FIRE_ANIMATION_NEW_SELECTED,
            mapOf(Pixel.PixelParameter.FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_INFERNO)
        )
    }

    @Test
    fun whenOnGlobalPrivacyControlClickedThenCommandIsLaunchGlobalPrivacyControl() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.onGlobalPrivacyControlClicked()

            assertEquals(Command.LaunchGlobalPrivacyControl, expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyClearWhatClickedEmitCommandShowClearWhatDialog() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.onAutomaticallyClearWhatClicked()

            assertEquals(Command.ShowClearWhatDialog(CLEAR_NONE), expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyClearWhenClickedEmitCommandShowClearWhenDialog() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.onAutomaticallyClearWhenClicked()

            assertEquals(Command.ShowClearWhenDialog(APP_EXIT_ONLY), expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnEmailProtectionSettingClickedThenEmitCommandLaunchEmailProtection() = coroutineTestRule.runBlocking {
        testee.commands().test {
            testee.onEmailProtectionSettingClicked()

            assertEquals(Command.LaunchEmailProtection, expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    private fun givenSelectedFireAnimation(fireAnimation: FireAnimation) {
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(fireAnimation)
        whenever(mockAppSettingsDataStore.isCurrentlySelected(fireAnimation)).thenReturn(true)
    }
}
