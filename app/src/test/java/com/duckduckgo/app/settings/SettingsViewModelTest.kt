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
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.SettingsViewModel.Companion.EMAIL_PROTECTION_URL
import com.duckduckgo.app.settings.SettingsViewModel.NetPEntryState.Hidden
import com.duckduckgo.app.settings.SettingsViewModel.NetPEntryState.Pending
import com.duckduckgo.app.settings.SettingsViewModel.NetPEntryState.ShowState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTING
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.sync.api.DeviceSyncState
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
    private lateinit var networkProtectionState: NetworkProtectionState

    @Mock
    private lateinit var mockEmailManager: EmailManager

    @Mock
    private lateinit var autofillCapabilityChecker: AutofillCapabilityChecker

    @Mock
    private lateinit var deviceSyncState: DeviceSyncState

    @Mock
    private lateinit var networkProtectionWaitlist: NetworkProtectionWaitlist

    @Mock
    private lateinit var mockAutoconsent: Autoconsent

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        runBlocking {
            whenever(networkProtectionWaitlist.getState()).thenReturn(NotUnlocked)
            whenever(networkProtectionState.isRunning()).thenReturn(false)
            whenever(networkProtectionState.isEnabled()).thenReturn(false)
            whenever(appTrackingProtection.isRunning()).thenReturn(false)
            whenever(appTrackingProtection.isEnabled()).thenReturn(false)
            whenever(appTrackingProtection.isOnboarded()).thenReturn(false)
        }

        testee = SettingsViewModel(
            mockDefaultBrowserDetector,
            appTrackingProtection,
            mockPixel,
            mockEmailManager,
            autofillCapabilityChecker,
            networkProtectionState,
            deviceSyncState,
            networkProtectionWaitlist,
            coroutineTestRule.testDispatcherProvider,
            mockAutoconsent,
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
    fun whenOnDefaultBrowserSettingClickedAndAlreadyDefaultBrowserThenLaunchDefaultBrowserCommandIsSentAndPixelFired() = runTest {
        testee.commands().test {
            whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
            testee.onDefaultBrowserSettingClicked()

            assertEquals(Command.LaunchDefaultBrowser, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_DEFAULT_BROWSER_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnDefaultBrowserSettingClickedAndNotDefaultBrowserThenLaunchDefaultBrowserCommandIsSentAndPixelFired() = runTest {
        testee.commands().test {
            whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
            testee.onDefaultBrowserSettingClicked()

            assertEquals(Command.LaunchDefaultBrowser, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_DEFAULT_BROWSER_PRESSED)

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
    fun whenOnEmailProtectionSettingClickedAndEmailIsSupportedThenEmitCommandLaunchEmailProtectionAndPixelFired() = runTest {
        whenever(mockEmailManager.isEmailFeatureSupported()).thenReturn(true)
        testee.commands().test {
            testee.onEmailProtectionSettingClicked()

            assertEquals(Command.LaunchEmailProtection(EMAIL_PROTECTION_URL), awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_EMAIL_PROTECTION_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnEmailProtectionSettingClickedAndEmailIsNotSupportedThenEmitCommandLaunchEmailProtectionNotSupportedAndPixelFired() = runTest {
        whenever(mockEmailManager.isEmailFeatureSupported()).thenReturn(false)
        testee.commands().test {
            testee.onEmailProtectionSettingClicked()

            assertEquals(Command.LaunchEmailProtectionNotSupported, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_EMAIL_PROTECTION_PRESSED)

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
    fun whenOnMacOsSettingClickedThenEmitCommandLaunchMacOsAndPixelFired() = runTest {
        testee.commands().test {
            testee.onMacOsSettingClicked()

            assertEquals(Command.LaunchMacOs, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_MAC_APP_PRESSED)

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
    fun whenWindowsSettingClickedThenEmitCommandLaunchWindows() = runTest {
        testee.commands().test {
            testee.windowsSettingClicked()

            assertEquals(Command.LaunchWindows, awaitItem())

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

            assertEquals(Command.LaunchWebTrackingProtectionScreen, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_WEB_TRACKING_PROTECTION_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAutofillSettingsClickThenEmitCommandLaunchAutofillSettings() = runTest {
        testee.commands().test {
            testee.onAutofillSettingsClick()

            assertEquals(Command.LaunchAutofillSettings, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppTPSettingClickedAndAppTpOnboardedThenEmitCommandLaunchAppTPTrackersScreenAndPixelFierd() = runTest {
        whenever(appTrackingProtection.isOnboarded()).thenReturn(true)
        testee.commands().test {
            testee.onAppTPSettingClicked()

            assertEquals(Command.LaunchAppTPTrackersScreen, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_APPTP_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAppTPSettingClickedAndAppTpNotOnboardedThenEmitCommandLaunchAppTPOnboardingAndPixelFired() = runTest {
        whenever(appTrackingProtection.isOnboarded()).thenReturn(false)
        testee.commands().test {
            testee.onAppTPSettingClicked()

            assertEquals(Command.LaunchAppTPOnboarding, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_APPTP_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNetPSettingClickedThenReturnScreenForCurrentState() = runTest {
        val testScreen = object : ActivityParams {}
        whenever(networkProtectionWaitlist.getScreenForCurrentState()).thenReturn(testScreen)

        testee.commands().test {
            testee.onNetPSettingClicked()

            assertEquals(Command.LaunchNetPWaitlist(testScreen), awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_NETP_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSyncSettingClickedThenEmitCommandLaunchSyncSettingsAndPixelFired() = runTest {
        testee.commands().test {
            testee.onSyncSettingClicked()

            assertEquals(Command.LaunchSyncSettings, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_SYNC_PRESSED)

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
    fun whenPermissionsSettingClickedThenEmitCommandLaunchPermissionsScreenAndPixelFired() = runTest {
        testee.commands().test {
            testee.onPermissionsSettingClicked()

            assertEquals(Command.LaunchPermissionsScreen, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_PERMISSIONS_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAboutSettingClickedThenEmitCommandLaunchAboutScreenAndPixelFired() = runTest {
        testee.commands().test {
            testee.onAboutSettingClicked()

            assertEquals(Command.LaunchAboutScreen, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_ABOUT_PRESSED)

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

    @Test
    fun whenOnAutoconsentClickedThenEmitCommandLaunchAutoconsentAndPixelFired() = runTest {
        testee.commands().test {
            testee.onCookiePopupProtectionSettingClicked()

            assertEquals(Command.LaunchCookiePopupProtectionScreen, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_COOKIE_POPUP_PROTECTION_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAutoconsentEnabledThenAutoconsentEnabledIsTrue() = runTest {
        whenever(mockAutoconsent.isSettingEnabled()).thenReturn(true)

        testee.start()

        testee.viewState().test {
            assertTrue(awaitItem().isAutoconsentEnabled)
        }
    }

    @Test
    fun whenAutoconsentDisabledThenAutoconsentEnabledIsFalse() = runTest {
        whenever(mockAutoconsent.isSettingEnabled()).thenReturn(false)

        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().isAutoconsentEnabled)
        }
    }

    @Test
    fun whenNetPIsNotUnlockedThenNetPEntryStateShouldShowHidden() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(NotUnlocked)

        testee.start()

        testee.viewState().test {
            assertEquals(
                Hidden,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsPendingInviteCodeThenNetPEntryStateShouldShowPending() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(PendingInviteCode)

        testee.start()

        testee.viewState().test {
            assertEquals(
                Pending,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsJoinedWaitlistThenNetPEntryStateShouldShowPending() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(JoinedWaitlist)

        testee.start()

        testee.viewState().test {
            assertEquals(
                Pending,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaButNotAcceptedTermsThenNetPEntryStateShouldShowPending() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(false))

        testee.start()

        testee.viewState().test {
            assertEquals(
                Pending,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaWithTermsAcceptedAndEnabledThenNetPEntryStateShouldCorrectShowState() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(true))

        testee.start()

        testee.viewState().test {
            assertEquals(
                ShowState(
                    icon = CheckListItem.CheckItemStatus.ENABLED,
                    subtitle = R.string.netpSettingsConnected,
                ),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaOnboardedAndEnabledThenNetPEntryStateShouldCorrectShowState() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(false))

        testee.start()

        testee.viewState().test {
            assertEquals(
                ShowState(
                    icon = CheckListItem.CheckItemStatus.ENABLED,
                    subtitle = R.string.netpSettingsConnected,
                ),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaAndConnectingThenNetPEntryStateShouldCorrectShowState() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(CONNECTING))
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(true))

        testee.start()

        testee.viewState().test {
            assertEquals(
                ShowState(
                    icon = CheckListItem.CheckItemStatus.ENABLED,
                    subtitle = R.string.netpSettingsConnecting,
                ),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaAndDisabledThenNetPEntryStateShouldCorrectShowState() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(false))

        testee.start()

        testee.viewState().test {
            assertEquals(
                ShowState(
                    icon = CheckListItem.CheckItemStatus.WARNING,
                    subtitle = R.string.netpSettingsDisconnected,
                ),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }
}
