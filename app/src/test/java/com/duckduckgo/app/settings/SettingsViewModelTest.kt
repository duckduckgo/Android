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
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchDataClearingSettingsScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchFireButtonScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchWhatsNew
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.remote.messaging.api.Content.MessageType
import com.duckduckgo.remote.messaging.impl.store.ModalSurfaceStore
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.voice.api.VoiceSearchAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    private val mockDuckAiFeatureState: DuckAiFeatureState = mock()
    private val duckAiShowSettingsFlow = MutableStateFlow(false)

    private val fakePostCtaExperienceToggles = FakeFeatureToggleFactory.create(PostCtaExperienceToggles::class.java)

    private val modalSurfaceStoreMock: ModalSurfaceStore = mock()

    @Before
    fun before() = runTest {
        whenever(dispatcherProviderMock.io()).thenReturn(coroutineTestRule.testDispatcher)
        whenever(appTrackingProtectionMock.isRunning()).thenReturn(true)
        whenever(autofillCapabilityCheckerMock.canAccessCredentialManagementScreen()).thenReturn(true)
        whenever(subscriptionsMock.isEligible()).thenReturn(true)
        whenever(mockDuckAiFeatureState.showSettings).thenReturn(duckAiShowSettingsFlow)

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
            duckAiFeatureState = mockDuckAiFeatureState,
            voiceSearchAvailability = voiceSearchAvailabilityMock,
            modalSurfaceStore = modalSurfaceStoreMock,
            privacyProUnifiedFeedback = privacyProUnifiedFeedbackMock,
            settingsPixelDispatcher = settingsPixelDispatcherMock,
            autofillFeature = autofillFeature,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            settingsPageFeature = fakeSettingsPageFeature,
            widgetCapabilities = mockWidgetCapabilities,
            postCtaExperienceToggles = fakePostCtaExperienceToggles,
        )
    }

    @Test
    fun `when ViewModel initialised then pixel is fired`() {
        testee // init
        verify(pixelMock).fire(AppPixelName.SETTINGS_OPENED)
        verify(pixelMock).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_SETTINGS_OPENED)
        verify(pixelMock).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_SETTINGS_OPENED_DAILY, type = Pixel.PixelType.Daily())
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
        fakePostCtaExperienceToggles.self().setRawStoredState(State(true))
        fakePostCtaExperienceToggles.simpleSearchWidgetPrompt().setRawStoredState(State(true))

        testee.userRequestedToAddHomeScreenWidget()

        testee.commands().test {
            assertEquals(LaunchAddHomeScreenWidget(true), awaitItem())
        }
    }

    @Test
    fun whenUserRequestedToAddHomeScreenWidgetAndNotSimpleWidgetThenLaunchAddHomeScreenWidgetCommandSentWithFalse() = runTest {
        fakePostCtaExperienceToggles.self().setRawStoredState(State(true))
        fakePostCtaExperienceToggles.simpleSearchWidgetPrompt().setRawStoredState(State(false))

        testee.userRequestedToAddHomeScreenWidget()

        testee.commands().test {
            assertEquals(LaunchAddHomeScreenWidget(false), awaitItem())
        }
    }

    @Test
    fun whenRefreshWidgetsInstalledStateAndWidgetsNewlyInstalledThenViewStateUpdatedAndCapabilitiesChecked() =
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
        }

    @Test
    fun whenRefreshWidgetsInstalledStateAndWidgetsNewlyUninstalledThenViewStateUpdatedAndCapabilitiesChecked() =
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
        }

    @Test
    fun `when duck AI settings disabled then state updated`() = runTest {
        duckAiShowSettingsFlow.value = false

        testee.viewState().test {
            assertFalse(awaitItem().isDuckChatEnabled)
        }
    }

    @Test
    fun `when duck AI settings enabled then state updated`() = runTest {
        duckAiShowSettingsFlow.value = true

        testee.viewState().test {
            assertTrue(awaitItem().isDuckChatEnabled)
        }
    }

    @Test
    fun `when fire button setting clicked and improved data clearing enabled then launch data clearing settings screen`() = runTest {
        fakeAndroidBrowserConfigFeature.improvedDataClearingOptions().setRawStoredState(State(true))

        testee.commands().test {
            testee.onFireButtonSettingClicked()

            assertEquals(LaunchDataClearingSettingsScreen, awaitItem())
        }
    }

    @Test
    fun `when fire button setting clicked and improved data clearing disabled then launch fire button screen`() = runTest {
        fakeAndroidBrowserConfigFeature.improvedDataClearingOptions().setRawStoredState(State(false))

        testee.commands().test {
            testee.onFireButtonSettingClicked()

            assertEquals(LaunchFireButtonScreen, awaitItem())
        }
    }

    @Test
    fun `when fire button setting clicked then pixel is fired`() = runTest {
        testee.onFireButtonSettingClicked()

        verify(pixelMock).fire(AppPixelName.SETTINGS_FIRE_BUTTON_PRESSED)
    }

    @Test
    fun `when whats new feature flag enabled and remote message exists then show whats new`() = runTest {
        fakeSettingsPageFeature.whatsNewEnabled().setRawStoredState(State(true))
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageId()).thenReturn("message-id")

        testee.start()

        assertTrue(testee.viewState().first().showWhatsNew)
    }

    @Test
    fun `when whats new feature flag enabled and no remote message then do not show whats new`() = runTest {
        fakeSettingsPageFeature.whatsNewEnabled().setRawStoredState(State(true))
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageId()).thenReturn(null)

        testee.start()

        assertFalse(testee.viewState().first().showWhatsNew)
    }

    @Test
    fun `when whats new feature flag disabled then do not show whats new`() = runTest {
        fakeSettingsPageFeature.whatsNewEnabled().setRawStoredState(State(false))
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageId()).thenReturn("message-id")

        testee.start()

        assertFalse(testee.viewState().first().showWhatsNew)
    }

    @Test
    fun `when whats new clicked and message id and type exist then launch whats new command is sent`() = runTest {
        val messageId = "test-message-id"
        val messageType = MessageType.MEDIUM
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageId()).thenReturn(messageId)
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageType()).thenReturn(messageType)

        testee.commands().test {
            testee.onWhatsNewClicked()

            assertEquals(LaunchWhatsNew(messageId, messageType), awaitItem())
        }
    }

    @Test
    fun `when whats new clicked and message id is null then no command is sent`() = runTest {
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageId()).thenReturn(null)
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageType()).thenReturn(MessageType.MEDIUM)

        testee.commands().test {
            testee.onWhatsNewClicked()

            expectNoEvents()
        }
    }

    @Test
    fun `when whats new clicked and message type is null then no command is sent`() = runTest {
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageId()).thenReturn("test-message-id")
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageType()).thenReturn(null)

        testee.commands().test {
            testee.onWhatsNewClicked()

            expectNoEvents()
        }
    }

    @Test
    fun `when what new clicked and message id and type exist then pixel event is fired`() = runTest {
        val messageId = "test-message-id"
        val messageType = MessageType.MEDIUM
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageId()).thenReturn(messageId)
        whenever(modalSurfaceStoreMock.getLastShownRemoteMessageType()).thenReturn(messageType)

        testee.onWhatsNewClicked()

        verify(pixelMock).fire(AppPixelName.SETTINGS_WHATS_NEW_PRESSED)
    }
}
