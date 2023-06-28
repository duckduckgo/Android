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
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.SettingsViewModel.Companion.EMAIL_PROTECTION_URL
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.vpn.FakeVpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.SyncState.READY
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.windows.api.WindowsDownloadLinkFeature
import com.duckduckgo.windows.api.WindowsWaitlist
import com.duckduckgo.windows.api.WindowsWaitlistFeature
import com.duckduckgo.windows.api.WindowsWaitlistState
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private lateinit var mockDefaultBrowserDetector: DefaultBrowserDetector

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var appTrackingProtection: AppTrackingProtection

    @Mock
    private lateinit var mockAppBuildConfig: AppBuildConfig

    @Mock
    private lateinit var mockEmailManager: EmailManager

    @Mock
    private lateinit var autofillCapabilityChecker: AutofillCapabilityChecker

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

    @Mock
    private lateinit var mockSyncStateMonitor: SyncStateMonitor

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    private val stateFlow = MutableStateFlow(READY)

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        val windowsFeature: WindowsWaitlistFeature = mock()
        whenever(windowsFeatureToggle.isEnabled()).thenReturn(false)
        whenever(windowsFeature.self()).thenReturn(windowsFeatureToggle)

        val mockWindowsDownloadLinkFeature: WindowsDownloadLinkFeature = mock()
        whenever(mockWindowsDownloadLinkToggle.isEnabled()).thenReturn(false)
        whenever(mockWindowsDownloadLinkFeature.self()).thenReturn(mockWindowsDownloadLinkToggle)

        whenever(mockAppBuildConfig.versionName).thenReturn("name")
        whenever(mockAppBuildConfig.versionCode).thenReturn(1)
        whenever(windowsWaitlist.getWaitlistState()).thenReturn(WindowsWaitlistState.NotJoinedQueue)

        whenever(mockNetPWaitlistRepository.getState(any())).thenReturn(NetPWaitlistState.NotUnlocked)

        whenever(mockSyncStateMonitor.syncState()).thenReturn(stateFlow.asStateFlow())

        vpnFeaturesRegistry = FakeVpnFeaturesRegistry()

        testee = SettingsViewModel(
            mockDefaultBrowserDetector,
            appTrackingProtection,
            mockPixel,
            mockAppBuildConfig,
            mockEmailManager,
            autofillCapabilityChecker,
            vpnFeaturesRegistry,
            windowsWaitlist,
            windowsFeature,
            deviceSyncState,
            mockSyncStateMonitor,
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
    fun whenOnDefaultBrowserSettingClickedAndAlreadyDefaultBrowserThenLaunchDefaultBrowserCommandIsSent() = runTest {
        testee.commands().test {
            whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
            testee.onDefaultBrowserSettingClicked()

            assertEquals(Command.LaunchDefaultBrowser, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnDefaultBrowserSettingClickedAndNotDefaultBrowserThenLaunchDefaultBrowserCommandIsSent() = runTest {
        testee.commands().test {
            whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
            testee.onDefaultBrowserSettingClicked()

            assertEquals(Command.LaunchDefaultBrowser, awaitItem())

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

            assertEquals(Command.LaunchEmailProtectionNotSupported, awaitItem())

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
    fun whenSyncFeatureDisabledThenViewStateIsCorrect() = runTest {
        whenever(deviceSyncState.isFeatureEnabled()).thenReturn(false)
        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().showSyncSetting)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSyncFeatureEnabledAndUserSignedInOnDeviceThenSettingVisible() = runTest {
        whenever(deviceSyncState.isFeatureEnabled()).thenReturn(true)
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(true)
        testee.start()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.showSyncSetting)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnLaunchedFromNotificationCalledWithPixelNameThePixelFired() {
        val pixelName = "pixel_name"
        testee.onLaunchedFromNotification(pixelName)

        verify(mockPixel).fire(pixelName)
    }

    @Test
    fun whenPrivateSearchSettingClickedThenEmitCommandLaunchPrivateSearchWebPageAndPixelFired() = runTest {
        testee.commands().test {
            testee.onPrivateSearchSettingClicked()

            assertEquals(Command.LaunchPrivateSearchWebPage, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_PRIVATE_SEARCH_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenWebTrackingProtectionSettingClickedThenEmitCommandLaunchWebTrackingProtectionWebPageAndPixelFired() = runTest {
        testee.commands().test {
            testee.onWebTrackingProtectionSettingClicked()

            assertEquals(Command.LaunchWebTrackingProtectionWebPage, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_WEB_TRACKING_PROTECTION_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAutofillSettingsClickThenEmitCommandLaunchAutofillSettingsAndPixelFired() = runTest {
        testee.commands().test {
            testee.onAutofillSettingsClick()

            assertEquals(Command.LaunchAutofillSettings, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_AUTOFILL_MANAGEMENT_OPENED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppTPSettingClickedAndAppTpOnboardedThenEmitCommandLaunchAppTPTrackersScreen() = runTest {
        whenever(appTrackingProtection.isOnboarded()).thenReturn(true)
        testee.commands().test {
            testee.onAppTPSettingClicked()

            assertEquals(Command.LaunchAppTPTrackersScreen, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppTPSettingClickedAndAppTpNotOnboardedThenEmitCommandLaunchAppTPOnboarding() = runTest {
        whenever(appTrackingProtection.isOnboarded()).thenReturn(false)
        testee.commands().test {
            testee.onAppTPSettingClicked()

            assertEquals(Command.LaunchAppTPOnboarding, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNetPSettingClickedAndInternalBuildInBetaThenEmitCommandLaunchNetPManagementScreen() = runTest {
        whenever(mockNetPWaitlistRepository.getState(any())).thenReturn(NetPWaitlistState.InBeta)
        testee.commands().test {
            testee.onNetPSettingClicked()

            assertEquals(Command.LaunchNetPManagementScreen, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNetPSettingClickedAndInternalBuildNotInBetaThenEmitCommandLaunchNetPWaitlist() = runTest {
        whenever(mockNetPWaitlistRepository.getState(any())).thenReturn(NetPWaitlistState.NotUnlocked)
        testee.commands().test {
            testee.onNetPSettingClicked()

            assertEquals(Command.LaunchNetPWaitlist, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSyncSettingClickedThenEmitCommandLaunchSyncSettings() = runTest {
        testee.commands().test {
            testee.onSyncSettingClicked()

            assertEquals(Command.LaunchSyncSettings, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAccessibilitySettingClickedThenEmitCommandLaunchAccessibilitySettingsAndPixelFired() = runTest {
        testee.commands().test {
            testee.onAccessibilitySettingClicked()

            assertEquals(Command.LaunchAccessibilitySettings, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_ACCESSIBILITY_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPermissionsAndPrivacySettingClickedThenEmitCommandLaunchPermissionsAndPrivacyScreenAndPixelFired() = runTest {
        testee.commands().test {
            testee.onPermissionsAndPrivacySettingClicked()

            assertEquals(Command.LaunchPermissionsAndPrivacyScreen, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_PERMISSIONS_AND_PRIVACY_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppearanceSettingClickedThenEmitCommandLaunchAppearanceScreenAndPixelFired() = runTest {
        testee.commands().test {
            testee.onAppearanceSettingClicked()

            assertEquals(Command.LaunchAppearanceScreen, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_APPEARANCE_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }
}
