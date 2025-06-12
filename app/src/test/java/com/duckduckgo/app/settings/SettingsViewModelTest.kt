/*
 * Copyright (c) 2025 DuckDuckGo
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

import android.annotation.SuppressLint
import app.cash.turbine.test
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAddHomeScreenWidget
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAutofillPasswordsManagement
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAutofillSettings
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.experiment.PostCtaExperienceExperiment
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.voice.api.VoiceSearchAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@SuppressLint("DenyListedApi")
class SettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val defaultWebBrowserCapabilityMock: DefaultBrowserDetector = mock()

    private val appTrackingProtectionMock: AppTrackingProtection = mock()

    private val pixelMock: Pixel = mock()

    private val emailManagerMock: EmailManager = mock()

    private val autofillCapabilityCheckerMock: AutofillCapabilityChecker = mock()

    private val deviceSyncStateMock: DeviceSyncState = mock()

    private val dispatcherProviderMock: DispatcherProvider = mock()

    private val autoconsentMock: Autoconsent = mock()

    private val subscriptionsMock: Subscriptions = mock()

    private val duckPlayerMock: DuckPlayer = mock()

    private val duckChatMock: DuckChat = mock()

    private val voiceSearchAvailabilityMock: VoiceSearchAvailability = mock()

    private val privacyProUnifiedFeedbackMock: PrivacyProUnifiedFeedback = mock()

    private val settingsPixelDispatcherMock: SettingsPixelDispatcher = mock()

    private lateinit var testee: SettingsViewModel

    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private val fakeAndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val fakeSettingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)

    private val mockWidgetCapabilities: WidgetCapabilities = mock()

    private val mockPostCtaExperienceExperiment: PostCtaExperienceExperiment = mock()

    @Before
    fun before() = runTest {
        whenever(dispatcherProviderMock.io()).thenReturn(coroutineTestRule.testDispatcher)
        whenever(appTrackingProtectionMock.isRunning()).thenReturn(true)
        whenever(autofillCapabilityCheckerMock.canAccessCredentialManagementScreen()).thenReturn(true)
        whenever(subscriptionsMock.isEligible()).thenReturn(true)

        testee = SettingsViewModel(
            defaultWebBrowserCapability = defaultWebBrowserCapabilityMock,
            appTrackingProtection = appTrackingProtectionMock,
            pixel = pixelMock,
            emailManager = emailManagerMock,
            autofillCapabilityChecker = autofillCapabilityCheckerMock,
            deviceSyncState = deviceSyncStateMock,
            dispatcherProvider = dispatcherProviderMock,
            autoconsent = autoconsentMock,
            subscriptions = subscriptionsMock,
            duckPlayer = duckPlayerMock,
            duckChat = duckChatMock,
            voiceSearchAvailability = voiceSearchAvailabilityMock,
            privacyProUnifiedFeedback = privacyProUnifiedFeedbackMock,
            settingsPixelDispatcher = settingsPixelDispatcherMock,
            autofillFeature = autofillFeature,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            settingsPageFeature = fakeSettingsPageFeature,
            widgetCapabilities = mockWidgetCapabilities,
            postCtaExperienceExperiment = mockPostCtaExperienceExperiment,
        )
    }

    @Test
    fun `when ViewModel initialised then pixel is fired`() {
        testee // init
        verify(pixelMock).fire(AppPixelName.SETTINGS_OPENED)
    }

    @Test
    fun `when sync pressed then pixel is fired`() {
        testee.onSyncSettingClicked()

        verify(settingsPixelDispatcherMock).fireSyncPressed()
    }

    @Test
    fun `when Duck Chat pressed then pixel is fired`() {
        testee.onDuckChatSettingClicked()

        verify(settingsPixelDispatcherMock).fireDuckChatPressed()
    }

    @Test
    fun `when Email pressed then pixel is fired`() {
        testee.onEmailProtectionSettingClicked()

        verify(settingsPixelDispatcherMock).fireEmailPressed()
    }

    @Test
    fun whenAutofillPressedThenNavigateToSettingsIfAutofillSettingsEnabled() = runTest {
        autofillFeature.settingsScreen().setRawStoredState(State(true))
        testee.onAutofillSettingsClick()

        testee.commands().test {
            assertEquals(LaunchAutofillSettings, awaitItem())
        }
    }

    @Test
    fun whenAutofillPressedThenNavigateToManagementIfAutofillSettingsDisabled() = runTest {
        autofillFeature.settingsScreen().setRawStoredState(State(false))
        testee.onAutofillSettingsClick()

        testee.commands().test {
            assertEquals(LaunchAutofillPasswordsManagement, awaitItem())
        }
    }

    @Test
    fun `when new threat protection settings is available then show threat protection settings`() = runTest {
        fakeAndroidBrowserConfigFeature.newThreatProtectionSettings().setRawStoredState(State(true))
        testee.start()
        assertTrue(testee.viewState().first().isNewThreatProtectionSettingsEnabled)
    }

    @Test
    fun whenWidgetAsProtectionFlagEnabledThenAddWidgetIsVisibleInProtectionsSection() = runTest {
        fakeSettingsPageFeature.self().setRawStoredState(State(true))
        fakeSettingsPageFeature.widgetAsProtection().setRawStoredState(State(true))
        testee.start()
        assertTrue(testee.viewState().first().isAddWidgetInProtectionsVisible)
    }

    @Test
    fun whenWidgetAsProtectionFlagDisabledThenAddWidgetIsNotVisibleInProtectionsSection() = runTest {
        fakeSettingsPageFeature.self().setRawStoredState(State(true))
        fakeSettingsPageFeature.widgetAsProtection().setRawStoredState(State(false))
        testee.start()
        assertFalse(testee.viewState().first().isAddWidgetInProtectionsVisible)
    }

    @Test
    fun whenUserRequestedToAddHomeScreenWidgetAndSimpleWidgetThenLaunchAddHomeScreenWidgetCommandSentWithTrue() = runTest {
        whenever(mockPostCtaExperienceExperiment.isSimpleSearchWidgetPrompt()).thenReturn(true)
        testee.userRequestedToAddHomeScreenWidget()

        verify(mockPostCtaExperienceExperiment).enroll()
        verify(mockPostCtaExperienceExperiment).fireSettingsWidgetDisplay()

        testee.commands().test {
            assertEquals(LaunchAddHomeScreenWidget(true), awaitItem())
        }
    }

    @Test
    fun whenUserRequestedToAddHomeScreenWidgetAndNotSimpleWidgetThenLaunchAddHomeScreenWidgetCommandSentWithFalse() = runTest {
        whenever(mockPostCtaExperienceExperiment.isSimpleSearchWidgetPrompt()).thenReturn(false)
        testee.userRequestedToAddHomeScreenWidget()

        verify(mockPostCtaExperienceExperiment).enroll()
        verify(mockPostCtaExperienceExperiment).fireSettingsWidgetDisplay()
        testee.commands().test {
            assertEquals(LaunchAddHomeScreenWidget(false), awaitItem())
        }
    }

    @Test
    fun whenRefreshWidgetsInstalledStateAndAddWidgetInProtectionsVisibleAndWidgetsNewlyInstalledThenViewStateUpdatedAndCapabilitiesChecked() =
        runTest {
            fakeSettingsPageFeature.self().setRawStoredState(State(true))
            fakeSettingsPageFeature.widgetAsProtection().setRawStoredState(State(true))
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false) // Initial state for start()
            testee.start()
            // Ensure initial state is as expected
            assertEquals(true, testee.viewState().value.isAddWidgetInProtectionsVisible)
            assertEquals(false, testee.viewState().value.widgetsInstalled)

            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true) // New state for refresh

            testee.refreshWidgetsInstalledState()

            assertEquals(true, testee.viewState().value.widgetsInstalled)
            verify(mockWidgetCapabilities, times(2)).hasInstalledWidgets
        }

    @Test
    fun whenRefreshWidgetsInstalledStateAndAddWidgetInProtectionsVisibleAndWidgetsNewlyUninstalledThenViewStateUpdatedAndCapabilitiesChecked() =
        runTest {
            fakeSettingsPageFeature.self().setRawStoredState(State(true))
            fakeSettingsPageFeature.widgetAsProtection().setRawStoredState(State(true))
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true) // Initial state for start()
            testee.start()
            // Ensure initial state is as expected
            assertEquals(true, testee.viewState().value.isAddWidgetInProtectionsVisible)
            assertEquals(true, testee.viewState().value.widgetsInstalled)

            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false) // New state for refresh

            testee.refreshWidgetsInstalledState()

            assertEquals(false, testee.viewState().value.widgetsInstalled)
            verify(mockWidgetCapabilities, times(2)).hasInstalledWidgets
        }

    @Test
    fun whenRefreshWidgetsInstalledStateAndAddWidgetInProtectionsNotVisibleThenViewStateUnchangedAndCapabilitiesNotCheckedByRefresh() =
        runTest {
            fakeSettingsPageFeature.self().setRawStoredState(State(true))
            fakeSettingsPageFeature.widgetAsProtection().setRawStoredState(State(false))
            val initialWidgetsInstalledState = true
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(initialWidgetsInstalledState) // Initial state for start()
            testee.start()
            // Ensure initial state is as expected
            assertEquals(false, testee.viewState().value.isAddWidgetInProtectionsVisible)
            assertEquals(initialWidgetsInstalledState, testee.viewState().value.widgetsInstalled)

            clearInvocations(mockWidgetCapabilities)
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(!initialWidgetsInstalledState)

            testee.refreshWidgetsInstalledState()

            assertEquals(initialWidgetsInstalledState, testee.viewState().value.widgetsInstalled)
            verify(mockWidgetCapabilities, never()).hasInstalledWidgets
        }
}
