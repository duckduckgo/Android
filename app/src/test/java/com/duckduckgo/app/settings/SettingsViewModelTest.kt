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

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.voice.api.VoiceSearchAvailability
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class SettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var defaultWebBrowserCapabilityMock: DefaultBrowserDetector

    @Mock
    private lateinit var appTrackingProtectionMock: AppTrackingProtection

    @Mock
    private lateinit var pixelMock: Pixel

    @Mock
    private lateinit var emailManagerMock: EmailManager

    @Mock
    private lateinit var autofillCapabilityCheckerMock: AutofillCapabilityChecker

    @Mock
    private lateinit var deviceSyncStateMock: DeviceSyncState

    @Mock
    private lateinit var dispatcherProviderMock: DispatcherProvider

    @Mock
    private lateinit var autoconsentMock: Autoconsent

    @Mock
    private lateinit var subscriptionsMock: Subscriptions

    @Mock
    private lateinit var duckPlayerMock: DuckPlayer

    @Mock
    private lateinit var duckChatMock: DuckChat

    @Mock
    private lateinit var voiceSearchAvailabilityMock: VoiceSearchAvailability

    @Mock
    private lateinit var privacyProUnifiedFeedbackMock: PrivacyProUnifiedFeedback

    @Mock
    private lateinit var settingsPixelDispatcherMock: SettingsPixelDispatcher

    private lateinit var testee: SettingsViewModel

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

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
}
