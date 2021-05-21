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
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.global.DuckDuckGoTheme
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.SettingsViewModel.EmailSetting
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_NONE
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_ONLY
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.vpn.apps.VpnExcludedInstalledAppInfo
import com.duckduckgo.mobile.android.vpn.apps.DeviceShieldExcludedApps
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboarding
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class SettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: SettingsViewModel

    private lateinit var context: Context

    @Mock
    private lateinit var commandObserver: Observer<Command>

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

    @Mock
    private lateinit var mockEmailManager: EmailManager

    @Mock
    private lateinit var mockDeviceShieldExcludedApps: DeviceShieldExcludedApps

    @Mock
    private lateinit var mockDeviceShieldOnboarding: DeviceShieldOnboarding

    @Mock
    private lateinit var deviceShieldPixels: DeviceShieldPixels

    private lateinit var commandCaptor: KArgumentCaptor<Command>

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        context = InstrumentationRegistry.getInstrumentation().targetContext
        commandCaptor = argumentCaptor()

        testee = SettingsViewModel(
            deviceShieldPixels,
            context,
            mockAppSettingsDataStore,
            mockDefaultBrowserDetector,
            mockVariantManager,
            mockEmailManager,
            mockFireAnimationLoader,
            mockPixel,
            mockDeviceShieldExcludedApps,
            mockDeviceShieldOnboarding
        )
        testee.command.observeForever(commandObserver)

        whenever(mockAppSettingsDataStore.automaticallyClearWhenOption).thenReturn(APP_EXIT_ONLY)
        whenever(mockAppSettingsDataStore.automaticallyClearWhatOption).thenReturn(CLEAR_NONE)
        whenever(mockAppSettingsDataStore.appIcon).thenReturn(AppIcon.DEFAULT)
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)

        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.DEFAULT_VARIANT)
        runBlocking { whenever(mockDeviceShieldExcludedApps.getExclusionAppList()).thenReturn(listOf()) }
    }

    @Test
    fun whenViewModelInitialisedThenPixelIsFired() {
        testee // init
        verify(mockPixel).fire(AppPixelName.SETTINGS_OPENED)
    }

    @Test
    fun whenStartNotCalledYetThenViewStateInitialisedDefaultValues() {
        assertNotNull(testee.viewState)

        val value = latestViewState()
        assertTrue(value.loading)
        assertEquals("", value.version)
        assertTrue(value.autoCompleteSuggestionsEnabled)
        assertFalse(value.showDefaultBrowserSetting)
        assertFalse(value.isAppDefaultBrowser)
    }

    @Test
    fun whenStartCalledThenLoadingSetToFalse() {
        testee.start()
        val value = latestViewState()
        assertEquals(false, value.loading)
    }

    @Test
    fun whenStartCalledThenVersionSetCorrectly() {
        testee.start()
        val value = latestViewState()
        val expectedStartString = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        assertTrue(value.version.startsWith(expectedStartString))
    }

    @Test
    fun whenStartCalledAndZeroExcludedAppsThenExcludedAppsInfoIsCorrect() {
        whenever(mockDeviceShieldExcludedApps.getExclusionAppList()).thenReturn(listOf())
        testee.start()
        assertEquals("None", latestViewState().excludedAppsInfo)
    }

    @Test
    fun whenStartCalledAndOneExcludedAppsThenExcludedAppsInfoIsCorrect() {
        whenever(mockDeviceShieldExcludedApps.getExclusionAppList())
            .thenReturn(
                listOf(
                    VpnExcludedInstalledAppInfo(
                        name = "foo",
                        packageName = "foo.com",
                        type = null
                    )
                )
            )
        testee.start()
        assertEquals("foo", latestViewState().excludedAppsInfo)
    }

    @Test
    fun whenStartCalledAndTwoExcludedAppsThenExcludedAppsInfoIsCorrect() {
        whenever(mockDeviceShieldExcludedApps.getExclusionAppList())
            .thenReturn(
                listOf(
                    VpnExcludedInstalledAppInfo(
                        name = "foo",
                        packageName = "foo.com",
                        type = null
                    ),
                    VpnExcludedInstalledAppInfo(
                        name = "bar",
                        packageName = "bar.com",
                        type = null
                    )
                )
            )
        testee.start()
        assertEquals("foo and bar", latestViewState().excludedAppsInfo)
    }

    @Test
    fun whenStartCalledAndThreeOrMoreExcludedAppsThenExcludedAppsInfoIsCorrect() {
        whenever(mockDeviceShieldExcludedApps.getExclusionAppList())
            .thenReturn(
                listOf(
                    VpnExcludedInstalledAppInfo(
                        name = "foo",
                        packageName = "foo.com",
                        type = null
                    ),
                    VpnExcludedInstalledAppInfo(
                        name = "bar",
                        packageName = "bar.com",
                        type = null
                    ),
                    VpnExcludedInstalledAppInfo(
                        name = "baz",
                        packageName = "baz.com",
                        type = null
                    )
                )
            )
        testee.start()
        assertEquals("foo, bar and more", latestViewState().excludedAppsInfo)
    }

    @Test
    fun whenLightThemeToggledOnThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() {
        testee.onLightThemeToggled(true)
        verify(mockAppSettingsDataStore).theme = DuckDuckGoTheme.LIGHT

        testee.command.blockingObserve()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.UpdateTheme, commandCaptor.firstValue)
    }

    @Test
    fun whenLightThemeToggledOnThenLighThemePixelIsSent() {
        testee.onLightThemeToggled(true)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT)
    }

    @Test
    fun whenLightThemeTogglesOffThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() {
        testee.onLightThemeToggled(false)
        verify(mockAppSettingsDataStore).theme = DuckDuckGoTheme.DARK

        testee.command.blockingObserve()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.UpdateTheme, commandCaptor.firstValue)
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
    fun whenDeviceShieldIsOffThenCommandIsStopDeviceShield() {
        testee.onDeviceShieldSettingChanged(false)
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.StopDeviceShield, commandCaptor.firstValue)
    }

    @Test
    fun whenDeviceShieldIsOffAndDidNotShowOnboardingThenCommandIsStopDeviceShield() {
        whenever(mockDeviceShieldOnboarding.prepare(context)).thenReturn(Intent())

        testee.onDeviceShieldSettingChanged(false)
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.StopDeviceShield, commandCaptor.firstValue)
    }

    @Test
    fun whenExcludedAppClickedThenCommandIsLaunchExcludedAppList() {
        testee.onExcludedAppsClicked()
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchExcludedAppList, commandCaptor.firstValue)
    }

    @Test
    fun whenBetaFeaturesClickedThenCommandIsLaunchBetaFeatures() {
        testee.onBetaFeatureSettingsClicked()
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBetaFeatures, commandCaptor.firstValue)
    }

    @Test
    fun whenDeviceShieldIsOnThenCommandIsStopDeviceShield() {
        testee.onDeviceShieldSettingChanged(true)
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.StartDeviceShield, commandCaptor.firstValue)
    }

    @Test
    fun whenDeviceShieldIsOnAndDidNotShowOnboardingThenCommandIsLaunchDeviceShieldOnboarding() {
        whenever(mockDeviceShieldOnboarding.prepare(context)).thenReturn(Intent())

        testee.onDeviceShieldSettingChanged(true)
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchDeviceShieldOnboarding, commandCaptor.firstValue)
    }

    @Test
    fun whenDeviceShieldIsOnThenSendPixel() {
        testee.onDeviceShieldSettingChanged(true)
        verify(deviceShieldPixels).enableFromSettings()
    }

    @Test
    fun whenDeviceShieldIsOffThenSendPixel() {
        testee.onDeviceShieldSettingChanged(false)
        verify(deviceShieldPixels).disableFromSettings()
    }

    @Test
    fun whenLeaveFeedBackRequestedThenCommandIsLaunchFeedback() {
        testee.userRequestedToSendFeedback()
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchFeedback, commandCaptor.firstValue)
    }

    @Test
    fun whenDefaultBrowserAppAlreadySetToOursThenIsDefaultBrowserFlagIsTrue() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        testee.start()
        val viewState = latestViewState()
        assertTrue(viewState.isAppDefaultBrowser)
    }

    @Test
    fun whenDefaultBrowserAppNotSetToOursThenIsDefaultBrowserFlagIsFalse() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        testee.start()
        val viewState = latestViewState()
        assertFalse(viewState.isAppDefaultBrowser)
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCannotBeSetThenFlagToShowSettingIsFalse() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
        testee.start()
        assertFalse(latestViewState().showDefaultBrowserSetting)
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCanBeSetThenFlagToShowSettingIsTrue() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
        testee.start()
        assertTrue(latestViewState().showDefaultBrowserSetting)
    }

    @Test
    fun whenWhitelistSelectedThenPixelIsSentAndWhitelistLaunched() {
        testee.onManageWhitelistSelected()
        verify(mockPixel).fire(AppPixelName.SETTINGS_MANAGE_WHITELIST)
        verify(commandObserver).onChanged(Command.LaunchWhitelist)
    }

    @Test
    fun whenVariantIsEmptyThenEmptyVariantIncludedInSettings() {
        testee.start()
        val expectedStartString = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        assertEquals(expectedStartString, latestViewState().version)
    }

    @Test
    fun whenVariantIsSetThenVariantKeyIncludedInSettings() {
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("ab", filterBy = { true }))
        testee.start()
        val expectedStartString = "${BuildConfig.VERSION_NAME} ab (${BuildConfig.VERSION_CODE})"
        assertEquals(expectedStartString, latestViewState().version)
    }

    @Test
    fun whenChangeIconRequestedThenCommandIsChangeIcon() {
        testee.userRequestedToChangeIcon()
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchAppIcon, commandCaptor.firstValue)
    }

    @Test
    fun whenFireAnimationSettingClickedThenCommandIsLaunchFireAnimationSettings() {
        testee.userRequestedToChangeFireAnimation()
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchFireAnimationSettings, commandCaptor.firstValue)
    }

    @Test
    fun whenFireAnimationSettingClickedThenPixelSent() {
        testee.userRequestedToChangeFireAnimation()

        verify(mockPixel).fire(AppPixelName.FIRE_ANIMATION_SETTINGS_OPENED)
    }

    @Test
    fun whenNewFireAnimationSelectedThenUpdateViewState() {
        testee.onFireAnimationSelected(FireAnimation.HeroWater)

        assertEquals(FireAnimation.HeroWater, latestViewState().selectedFireAnimation)
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
    fun whenOnGlobalPrivacyControlClickedThenCommandIsLaunchGlobalPrivacyControl() {
        testee.onGlobalPrivacyControlClicked()
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchGlobalPrivacyControl, commandCaptor.firstValue)
    }

    @Test
    fun whenUserNotSignedInOnEmailThenEmailSettingIsOff() {
        givenUserIsNotSignedIn()

        testee.start()

        assert(latestViewState().emailSetting is EmailSetting.EmailSettingOff)
    }

    @Test
    fun whenUserSignedInOnEmailAndEmailAddressIsNotNullThenEmailSettingIsOn() {
        givenUserIsSignedInAndHasAliasAvailable()

        testee.start()

        assertTrue(latestViewState().emailSetting is EmailSetting.EmailSettingOn)
    }

    @Test
    fun whenUserSignedInOnEmailAndEmailAddressIsNullThenEmailSettingIsOff() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn(null)
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)

        testee.start()

        assert(latestViewState().emailSetting is EmailSetting.EmailSettingOff)
    }

    @Test
    fun whenOnEmailLogoutThenSignOutIsCalled() {
        testee.onEmailLogout()

        verify(mockEmailManager).signOut()
    }

    @Test
    fun whenOnEmailLogoutThenEmailSettingIsOff() {
        testee.onEmailLogout()

        assert(latestViewState().emailSetting is EmailSetting.EmailSettingOff)
    }

    @Test
    fun whenOnEmailSettingClickedAndUserIsSignedInThenLaunchEmailDialogCommandSent() {
        givenUserIsSignedInAndHasAliasAvailable()

        testee.onEmailSettingClicked()

        assertCommandIssued<Command.LaunchEmailDialog>()
    }

    private fun latestViewState() = testee.viewState.value!!

    private inline fun <reified T : Command> assertCommandIssued(instanceAssertions: T.() -> Unit = {}) {
        Mockito.verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is T }
        assertNotNull(issuedCommand)
        (issuedCommand as T).apply { instanceAssertions() }
    }

    private fun givenUserIsSignedInAndHasAliasAvailable() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("test@duck.com")
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
    }

    private fun givenUserIsNotSignedIn() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
    }

    private fun givenSelectedFireAnimation(fireAnimation: FireAnimation) {
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(fireAnimation)
        whenever(mockAppSettingsDataStore.isCurrentlySelected(fireAnimation)).thenReturn(true)
    }
}
