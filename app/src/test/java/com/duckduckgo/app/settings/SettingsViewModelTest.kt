/*
 * Copyright (c) 2022 DuckDuckGo
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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.SettingsViewModel.Companion.EMAIL_PROTECTION_URL
import com.duckduckgo.app.settings.clear.AppLinkSettingType
import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_NONE
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_ONLY
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.store.ThemingDataStore
import com.duckduckgo.mobile.android.vpn.FakeVpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.windows.api.WindowsDownloadLinkFeature
import com.duckduckgo.windows.api.WindowsWaitlist
import com.duckduckgo.windows.api.WindowsWaitlistFeature
import com.duckduckgo.windows.api.WindowsWaitlistState
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
@ExperimentalTime
class SettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: SettingsViewModel

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

    @Mock
    private lateinit var mockGpc: Gpc

    @Mock
    private lateinit var mockFeatureToggle: FeatureToggle

    @Mock
    private lateinit var appTrackingProtection: AppTrackingProtection

    @Mock
    private lateinit var mockAppBuildConfig: AppBuildConfig

    @Mock
    private lateinit var mockEmailManager: EmailManager

    @Mock
    private lateinit var autofillCapabilityChecker: AutofillCapabilityChecker

    @Mock
    private lateinit var autoconsent: Autoconsent

    @Mock
    private lateinit var windowsWaitlist: WindowsWaitlist

    @Mock
    private lateinit var windowsFeatureToggle: Toggle

    @Mock
    private lateinit var deviceSyncState: DeviceSyncState

    @Mock
    private lateinit var mockNetPWaitlistRepository: NetPWaitlistRepository

    @Mock
    private lateinit var mockWindowsDownloadLinkToggle: Toggle

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        val windowsFeature: WindowsWaitlistFeature = mock()
        whenever(windowsFeatureToggle.isEnabled()).thenReturn(false)
        whenever(windowsFeature.self()).thenReturn(windowsFeatureToggle)

        val mockWindowsDownloadLinkFeature: WindowsDownloadLinkFeature = mock()
        whenever(mockWindowsDownloadLinkToggle.isEnabled()).thenReturn(false)
        whenever(mockWindowsDownloadLinkFeature.self()).thenReturn(mockWindowsDownloadLinkToggle)

        whenever(mockAppSettingsDataStore.automaticallyClearWhenOption).thenReturn(APP_EXIT_ONLY)
        whenever(mockAppSettingsDataStore.automaticallyClearWhatOption).thenReturn(CLEAR_NONE)
        whenever(mockAppSettingsDataStore.appIcon).thenReturn(AppIcon.DEFAULT)
        whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.LIGHT)
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)
        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.DEFAULT_VARIANT)
        whenever(mockAppBuildConfig.versionName).thenReturn("name")
        whenever(mockAppBuildConfig.versionCode).thenReturn(1)
        whenever(windowsWaitlist.getWaitlistState()).thenReturn(WindowsWaitlistState.NotJoinedQueue)

        whenever(mockNetPWaitlistRepository.getState(any())).thenReturn(NetPWaitlistState.NotUnlocked)

        vpnFeaturesRegistry = FakeVpnFeaturesRegistry()

        testee = SettingsViewModel(
            mockThemeSettingsDataStore,
            mockAppSettingsDataStore,
            mockDefaultBrowserDetector,
            mockVariantManager,
            mockFireAnimationLoader,
            appTrackingProtection,
            mockGpc,
            mockFeatureToggle,
            mockPixel,
            mockAppBuildConfig,
            mockEmailManager,
            autofillCapabilityChecker,
            vpnFeaturesRegistry,
            autoconsent,
            windowsWaitlist,
            windowsFeature,
            deviceSyncState,
            mockNetPWaitlistRepository,
            mockWindowsDownloadLinkFeature,
            coroutineTestRule.testDispatcherProvider,
        )

        runTest {
            whenever(autofillCapabilityChecker.canAccessCredentialManagementScreen()).thenReturn(true)
        }
    }

    @Test
    fun whenViewModelInitialisedThenPixelIsFired() {
        testee // init
        verify(mockPixel).fire(AppPixelName.SETTINGS_OPENED)
    }

    @Test
    fun whenStartIfGpcToggleDisabledAndGpcEnabledThenGpgDisabled() = runTest {
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.GpcFeatureName.value), any())).thenReturn(false)
        whenever(mockGpc.isEnabled()).thenReturn(true)

        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().globalPrivacyControlEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartIfGpcToggleEnabledAndGpcDisabledThenGpgDisabled() = runTest {
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.GpcFeatureName.value), any())).thenReturn(true)
        whenever(mockGpc.isEnabled()).thenReturn(false)
        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().globalPrivacyControlEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartIfGpcToggleEnabledAndGpcEnabledThenGpgEnabled() = runTest {
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.GpcFeatureName.value), any())).thenReturn(true)
        whenever(mockGpc.isEnabled()).thenReturn(true)
        testee.start()

        testee.viewState().test {
            assertTrue(awaitItem().globalPrivacyControlEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartNotCalledYetThenViewStateInitialisedDefaultValues() = runTest {
        testee.viewState().test {
            val value = awaitItem()
            assertTrue(value.loading)
            assertEquals("", value.version)
            assertTrue(value.autoCompleteSuggestionsEnabled)
            assertFalse(value.showDefaultBrowserSetting)
            assertFalse(value.isAppDefaultBrowser)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartCalledThenLoadingSetToFalse() = runTest {
        testee.start()
        testee.viewState().test {
            val value = awaitItem()
            assertEquals(false, value.loading)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartCalledThenVersionSetCorrectly() = runTest {
        testee.start()
        testee.viewState().test {
            val value = expectMostRecentItem()
            val expectedStartString = "name (1)"
            assertTrue(value.version.startsWith(expectedStartString))

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartCalledThenEmailAddressSetCorrectly() = runTest {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("email")
        testee.start()
        testee.viewState().test {
            val value = expectMostRecentItem()
            val expectedEmail = "email"
            assertEquals(expectedEmail, value.emailAddress)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartCalledWithNotificationsEnabledThenNotificationsSettingSubtitleSetCorrectlyAsEnabled() = runTest {
        testee.start(notificationsEnabled = true)

        testee.viewState().test {
            val value = expectMostRecentItem()
            assertEquals(R.string.settingsSubtitleNotificationsEnabled, value.notificationsSettingSubtitleId)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartCalledWithNotificationsDisabledThenNotificationsSettingSubtitleSetCorrectlyAsDisabled() = runTest {
        testee.start(notificationsEnabled = false)

        testee.viewState().test {
            val value = expectMostRecentItem()
            assertEquals(R.string.settingsSubtitleNotificationsDisabled, value.notificationsSettingSubtitleId)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenThemeSettingsClickedThenPixelSent() {
        testee.userRequestedToChangeTheme()
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_OPENED)
    }

    @Test
    fun whenThemeSettingsClickedThenCommandIsLaunchThemeSettingsIsSent() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeTheme()

            assertEquals(Command.LaunchThemeSettings(DuckDuckGoTheme.LIGHT), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenThemeChangedThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() = runTest {
        testee.commands().test {
            givenThemeSelected(DuckDuckGoTheme.LIGHT)
            testee.onThemeSelected(DuckDuckGoTheme.DARK)

            verify(mockThemeSettingsDataStore).theme = DuckDuckGoTheme.DARK

            assertEquals(Command.UpdateTheme, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenThemeChangedToLightThenLightThemePixelIsSent() {
        givenThemeSelected(DuckDuckGoTheme.DARK)
        testee.onThemeSelected(DuckDuckGoTheme.LIGHT)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT)
    }

    @Test
    fun whenThemeChangedToDarkThenDarkThemePixelIsSent() {
        givenThemeSelected(DuckDuckGoTheme.LIGHT)
        testee.onThemeSelected(DuckDuckGoTheme.DARK)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_DARK)
    }

    @Test
    fun whenThemeChangedToSystemDefaultThenSystemDefaultThemePixelIsSent() {
        givenThemeSelected(DuckDuckGoTheme.LIGHT)
        testee.onThemeSelected(DuckDuckGoTheme.SYSTEM_DEFAULT)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_SYSTEM_DEFAULT)
    }

    @Test
    fun whenThemeChangedButThemeWasAlreadySetThenDoNothing() = runTest {
        testee.commands().test {
            givenThemeSelected(DuckDuckGoTheme.LIGHT)
            testee.onThemeSelected(DuckDuckGoTheme.LIGHT)

            verify(mockPixel, never()).fire(AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT)
            verify(mockThemeSettingsDataStore, never()).theme = DuckDuckGoTheme.LIGHT

            expectNoEvents()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDefaultBrowserTogglesOffThenLaunchDefaultBrowserCommandIsSent() = runTest {
        testee.commands().test {
            whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
            testee.onDefaultBrowserToggled(false)

            assertEquals(Command.LaunchDefaultBrowser, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDefaultBrowserTogglesOnThenLaunchDefaultBrowserCommandIsSent() = runTest {
        testee.commands().test {
            whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
            testee.onDefaultBrowserToggled(true)

            assertEquals(Command.LaunchDefaultBrowser, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDefaultBrowserTogglesOnAndBrowserWasAlreadyDefaultThenLaunchDefaultBrowserCommandIsNotSent() =
        runTest {
            testee.commands().test {
                whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
                testee.onDefaultBrowserToggled(true)

                expectNoEvents()

                cancelAndConsumeRemainingEvents()
            }
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
    fun whenAppLinksSetToAskEverytimeThenDataStoreIsUpdatedAndPixelIsSent() {
        testee.onAppLinksSettingChanged(AppLinkSettingType.ASK_EVERYTIME)
        verify(mockAppSettingsDataStore).appLinksEnabled = true
        verify(mockAppSettingsDataStore).showAppLinksPrompt = true

        verify(mockPixel).fire(
            AppPixelName.SETTINGS_APP_LINKS_ASK_EVERY_TIME_SELECTED,
        )
    }

    @Test
    fun whenAppLinksSetToAlwaysThenDataStoreIsUpdatedAndPixelIsSent() {
        testee.onAppLinksSettingChanged(AppLinkSettingType.ALWAYS)
        verify(mockAppSettingsDataStore).appLinksEnabled = true
        verify(mockAppSettingsDataStore).showAppLinksPrompt = false

        verify(mockPixel).fire(
            AppPixelName.SETTINGS_APP_LINKS_ALWAYS_SELECTED,
        )
    }

    @Test
    fun whenAppLinksSetToNeverThenDataStoreIsUpdatedAndPixelIsSent() {
        testee.onAppLinksSettingChanged(AppLinkSettingType.NEVER)
        verify(mockAppSettingsDataStore).appLinksEnabled = false
        verify(mockAppSettingsDataStore).showAppLinksPrompt = false

        verify(mockPixel).fire(
            AppPixelName.SETTINGS_APP_LINKS_NEVER_SELECTED,
        )
    }

    @Test
    fun whenLeaveFeedBackRequestedThenCommandIsLaunchFeedback() = runTest {
        testee.commands().test {
            testee.userRequestedToSendFeedback()

            assertEquals(Command.LaunchFeedback, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDefaultBrowserAppAlreadySetToOursThenIsDefaultBrowserFlagIsTrue() = runTest {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        testee.start()

        testee.viewState().test {
            assertTrue(awaitItem().isAppDefaultBrowser)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDefaultBrowserAppNotSetToOursThenIsDefaultBrowserFlagIsFalse() = runTest {
        testee.viewState().test {
            whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
            testee.start()

            assertFalse(awaitItem().isAppDefaultBrowser)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCannotBeSetThenFlagToShowSettingIsFalse() = runTest {
        testee.viewState().test {
            whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
            testee.start()

            assertFalse(awaitItem().showDefaultBrowserSetting)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCanBeSetThenFlagToShowSettingIsTrue() = runTest {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
        testee.start()
        testee.viewState().test {
            assertTrue(awaitItem().showDefaultBrowserSetting)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenWhitelistSelectedThenPixelIsSentAndWhitelistLaunched() = runTest {
        testee.commands().test {
            testee.onManageWhitelistSelected()

            verify(mockPixel).fire(AppPixelName.SETTINGS_MANAGE_WHITELIST)
            assertEquals(Command.LaunchWhitelist, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVariantIsEmptyThenEmptyVariantIncludedInSettings() = runTest {
        testee.start()
        testee.viewState().test {
            val expectedStartString = "name (1)"
            assertEquals(expectedStartString, awaitItem().version)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVariantIsSetThenVariantKeyIncludedInSettings() = runTest {
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("ab", filterBy = { true }))
        testee.start()

        testee.viewState().test {
            val expectedStartString = "name ab (1)"
            assertEquals(expectedStartString, awaitItem().version)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenChangeIconRequestedThenCommandIsChangeIcon() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeIcon()

            assertEquals(Command.LaunchAppIcon, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFireAnimationSettingClickedThenCommandIsLaunchFireAnimationSettings() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeFireAnimation()

            assertEquals(Command.LaunchFireAnimationSettings(FireAnimation.HeroFire), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFireAnimationSettingClickedThenPixelSent() {
        testee.userRequestedToChangeFireAnimation()

        verify(mockPixel).fire(AppPixelName.FIRE_ANIMATION_SETTINGS_OPENED)
    }

    @Test
    fun whenNewFireAnimationSelectedThenUpdateViewState() = runTest {
        val expectedAnimation = FireAnimation.HeroAbstract
        testee.onFireAnimationSelected(expectedAnimation)

        testee.viewState().test {
            assertEquals(expectedAnimation, awaitItem().selectedFireAnimation)

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
            mapOf(Pixel.PixelParameter.FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_WHIRLPOOL),
        )
    }

    @Test
    fun whenSameFireAnimationSelectedThenDoNotSendPixel() {
        givenSelectedFireAnimation(FireAnimation.HeroFire)

        testee.onFireAnimationSelected(FireAnimation.HeroFire)

        verify(mockPixel, times(0)).fire(
            AppPixelName.FIRE_ANIMATION_NEW_SELECTED,
            mapOf(Pixel.PixelParameter.FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_INFERNO),
        )
    }

    @Test
    fun whenOnGlobalPrivacyControlClickedThenCommandIsLaunchGlobalPrivacyControl() = runTest {
        testee.commands().test {
            testee.onGlobalPrivacyControlClicked()

            assertEquals(Command.LaunchGlobalPrivacyControl, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyClearWhatClickedEmitCommandShowClearWhatDialog() = runTest {
        testee.commands().test {
            testee.onAutomaticallyClearWhatClicked()

            assertEquals(Command.ShowClearWhatDialog(CLEAR_NONE), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyClearWhenClickedEmitCommandShowClearWhenDialog() = runTest {
        testee.commands().test {
            testee.onAutomaticallyClearWhenClicked()

            assertEquals(Command.ShowClearWhenDialog(APP_EXIT_ONLY), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnEmailProtectionSettingClickedAndEmailIsSupportedThenEmitCommandLaunchEmailProtection() = runTest {
        whenever(mockEmailManager.isEmailFeatureSupported()).thenReturn(true)
        testee.commands().test {
            testee.onEmailProtectionSettingClicked()

            assertEquals(Command.LaunchEmailProtection(EMAIL_PROTECTION_URL), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnEmailProtectionSettingClickedAndEmailIsNotSupportedThenEmitCommandLaunchEmailProtectionNotSupported() = runTest {
        whenever(mockEmailManager.isEmailFeatureSupported()).thenReturn(false)
        testee.commands().test {
            testee.onEmailProtectionSettingClicked()

            assertEquals(Command.LaunchEmailProtectionNotSUpported, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenHomeScreenWidgetSettingClickedThenEmitCommandLaunchAddHomeScreenWidget() = runTest {
        testee.commands().test {
            testee.userRequestedToAddHomeScreenWidget()

            assertEquals(Command.LaunchAddHomeScreenWidget, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnMacOsSettingClickedThenEmitCommandLaunchMacOs() = runTest {
        testee.commands().test {
            testee.onMacOsSettingClicked()

            assertEquals(Command.LaunchMacOs, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAutofillIsAvailableTheShowAutofillTrue() = runTest {
        whenever(autofillCapabilityChecker.canAccessCredentialManagementScreen()).thenReturn(true)
        testee.start()

        testee.viewState().test {
            assertTrue(awaitItem().showAutofill)
        }
    }

    @Test
    fun whenAutofillIsNotAvailableTheShowAutofillFalse() = runTest {
        whenever(autofillCapabilityChecker.canAccessCredentialManagementScreen()).thenReturn(false)
        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().showAutofill)
        }
    }

    @Test
    fun whenOnAutoconsentClickedThenEmitCommandLaunchAutoconsent() = runTest {
        testee.commands().test {
            testee.onAutoconsentClicked()

            assertEquals(Command.LaunchAutoconsent, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAutoconsentEnabledThenAutoconsentEnabledIsTrue() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)
        testee.start()

        testee.viewState().test {
            assertTrue(awaitItem().autoconsentEnabled)
        }
    }

    @Test
    fun whenAutoconsentDisabledThenAutoconsentEnabledIsFalse() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)
        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().autoconsentEnabled)
        }
    }

    @Test
    fun whenAppTPOnboardingNotShownThenViewStateIsCorrect() = runTest {
        whenever(appTrackingProtection.isOnboarded()).thenReturn(false)
        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().appTrackingProtectionOnboardingShown)
        }
    }

    @Test
    fun whenAppTPOnboardingShownThenViewStateIsCorrect() = runTest {
        whenever(appTrackingProtection.isOnboarded()).thenReturn(true)
        testee.start()

        testee.viewState().test {
            assertTrue(awaitItem().appTrackingProtectionOnboardingShown)
        }
    }

    @Test
    fun whenWindowsSettingClickedAndWindowsFeatureEnabledThenEmitCommandLaunchWindows() = runTest {
        whenever(mockWindowsDownloadLinkToggle.isEnabled()).thenReturn(true)

        testee.commands().test {
            testee.windowsSettingClicked()

            assertEquals(Command.LaunchWindows, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenWindowsSettingClickedAndWindowsFeatureDisabledThenEmitCommandLaunchWindowsWaitlist() = runTest {
        whenever(mockWindowsDownloadLinkToggle.isEnabled()).thenReturn(false)

        testee.commands().test {
            testee.windowsSettingClicked()

            assertEquals(Command.LaunchWindowsWaitlist, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserRequestedToChangeNotificationsSettingThenEmitLaunchNotificationsSettingsAndSendPixel() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeNotificationsSetting()

            assertEquals(Command.LaunchNotificationsSettings, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_NOTIFICATIONS_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSyncFeatureDisabledThenViewStateIsCorrect() = runTest {
        whenever(deviceSyncState.isFeatureEnabled()).thenReturn(false)
        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().showSyncSetting)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSyncFeatureEnabledAndUserSignedInOnDeviceThenSettingVisibleAndStateEnabled() = runTest {
        whenever(deviceSyncState.isFeatureEnabled()).thenReturn(true)
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(true)
        testee.start()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.showSyncSetting)
            assertTrue(viewState.syncEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnLaunchedFromNotificationCalledWithPixelNameThePixelFired() {
        val pixelName = "pixel_name"
        testee.onLaunchedFromNotification(pixelName)

        verify(mockPixel).fire(pixelName)
    }

    private fun givenSelectedFireAnimation(fireAnimation: FireAnimation) {
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(fireAnimation)
        whenever(mockAppSettingsDataStore.isCurrentlySelected(fireAnimation)).thenReturn(true)
    }

    private fun givenThemeSelected(theme: DuckDuckGoTheme) {
        whenever(mockThemeSettingsDataStore.theme).thenReturn(theme)
        whenever(mockThemeSettingsDataStore.isCurrentlySelected(theme)).thenReturn(true)
    }
}
