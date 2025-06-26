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

package com.duckduckgo.duckchat.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.feature.AIChatImageUploadFeature
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewActivityWithParams
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealDuckChatTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDuckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val moshi: Moshi = Moshi.Builder().build()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider
    private val mockGlobalActivityStarter: GlobalActivityStarter = mock()
    private val mockContext: Context = mock()
    private val mockPixel: Pixel = mock()
    private val mockIntent: Intent = mock()
    private val mockBrowserNav: BrowserNav = mock()
    private val imageUploadFeature: AIChatImageUploadFeature = FakeFeatureToggleFactory.create(AIChatImageUploadFeature::class.java)
    private val mockVisualDesignExperimentDataStore: VisualDesignExperimentDataStore = mock()
    private val isVisualDesignEnabledStateFlow = MutableStateFlow(false)

    private lateinit var testee: RealDuckChat

    @Before
    fun setup() = runTest {
        whenever(mockDuckChatFeatureRepository.shouldShowInBrowserMenu()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(false)
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.isInputScreenUserSettingEnabled()).thenReturn(true)
        isVisualDesignEnabledStateFlow.value = true
        whenever(mockDuckChatFeatureRepository.sessionDeltaInMinutes()).thenReturn(10L)
        whenever(mockContext.getString(any())).thenReturn("Duck.ai")
        whenever(mockVisualDesignExperimentDataStore.isExperimentEnabled).thenReturn(isVisualDesignEnabledStateFlow)
        duckChatFeature.self().setRawStoredState(State(enable = true))
        duckChatFeature.duckAiInputScreen().setRawStoredState(State(enable = true))
        imageUploadFeature.self().setRawStoredState(State(enable = true))

        testee = spy(
            RealDuckChat(
                mockDuckChatFeatureRepository,
                duckChatFeature,
                mockVisualDesignExperimentDataStore,
                moshi,
                dispatcherProvider,
                mockGlobalActivityStarter,
                mockContext,
                true,
                coroutineRule.testScope,
                mockPixel,
                imageUploadFeature,
                mockBrowserNav,
            ),
        )
        coroutineRule.testScope.advanceUntilIdle()

        whenever(mockGlobalActivityStarter.startIntent(any(), any<DuckChatWebViewActivityWithParams>())).thenReturn(mockIntent)
    }

    @Test
    fun whenSetShowInBrowserMenuSetTrueThenPixelOnIsSent() = runTest {
        testee.setShowInBrowserMenuUserSetting(true)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_ON)
    }

    @Test
    fun whenSetShowInBrowserMenuSetFalseThenPixelOffIsSent() = runTest {
        testee.setShowInBrowserMenuUserSetting(false)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_OFF)
    }

    @Test
    fun whenSetShowInBrowserMenuUserSettingThenRepositorySetCalled() = runTest {
        testee.setShowInBrowserMenuUserSetting(true)
        verify(mockDuckChatFeatureRepository).setShowInBrowserMenu(true)
    }

    @Test
    fun whenSetShowInAddressBarSetTrueThenPixelOnIsSent() = runTest {
        testee.setShowInAddressBarUserSetting(true)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_ON)
    }

    @Test
    fun whenSetShowInAddressBarSetFalseThenPixelOffIsSent() = runTest {
        testee.setShowInAddressBarUserSetting(false)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_OFF)
    }

    @Test
    fun whenSetShowInAddressBarUserSettingThenRepositorySetCalled() = runTest {
        testee.setShowInAddressBarUserSetting(true)
        verify(mockDuckChatFeatureRepository).setShowInAddressBar(true)
    }

    @Test
    fun whenDuckChatIsEnabledThenReturnTrue() = runTest {
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenDuckChatIsDisabledThenReturnFalse() = runTest {
        duckChatFeature.self().setRawStoredState(State(false))

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.isEnabled())
    }

    @Test
    fun whenObserveShowInBrowserMenuUserSettingThenEmitCorrectValues() = runTest {
        whenever(mockDuckChatFeatureRepository.observeShowInBrowserMenu()).thenReturn(flowOf(true, false))

        val results = testee.observeShowInBrowserMenuUserSetting().take(2).toList()
        assertTrue(results[0])
        assertFalse(results[1])
    }

    @Test
    fun whenObserveShowInAddressBarUserSettingThenEmitCorrectValues() = runTest {
        whenever(mockDuckChatFeatureRepository.observeShowInAddressBar()).thenReturn(flowOf(true, false))

        val results = testee.observeShowInAddressBarUserSetting().take(2).toList()
        assertTrue(results[0])
        assertFalse(results[1])
    }

    @Test
    fun whenFeatureEnabledThenShowInBrowserMenuReturnsValueFromRepository() {
        assertTrue(testee.showPopupMenuShortcut.value)
    }

    @Test
    fun whenFeatureDisabledThenShowInBrowserMenuReturnsFalse() {
        duckChatFeature.self().setRawStoredState(State(false))

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.showPopupMenuShortcut.value)
    }

    @Test
    fun whenConfigSetsAddressBarEntryPointTrueThenIsAddressBarEntryPointEnabledReturnsTrue() = runTest {
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR,
            ),
        )
        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.isAddressBarEntryPointEnabled())
    }

    @Test
    fun whenConfigSetsAddressBarEntryPointFalseThenIsAddressBarEntryPointEnabledReturnsFalse() = runTest {
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR_ENTRY_POINT_DISABLED,
            ),
        )
        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.isAddressBarEntryPointEnabled())
    }

    @Test
    fun showInAddressBarReturnsCorrectValueBasedOnUserSettingAndConfig() = runTest {
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR,
            ),
        )
        testee.onPrivacyConfigDownloaded()
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(true)

        testee.setShowInAddressBarUserSetting(true)
        assertTrue(testee.showOmnibarShortcutOnNtpAndOnFocus.value)

        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(false)
        testee.setShowInAddressBarUserSetting(false)
        assertFalse(testee.showOmnibarShortcutOnNtpAndOnFocus.value)
    }

    @Test
    fun whenOpenDuckChatCalledThenActivityStarted() = runTest {
        testee.openDuckChat()
        verify(mockGlobalActivityStarter).startIntent(
            mockContext,
            DuckChatWebViewActivityWithParams(
                url = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5",
            ),
        )
        verify(mockContext).startActivity(any())
        verify(mockDuckChatFeatureRepository).registerOpened()
    }

    @Test
    fun whenOpenDuckChatCalledAndPoCIsDisabledThenPoCWebViewActivityNotStarted() = runTest {
        whenever(
            mockGlobalActivityStarter.startIntent(
                mockContext,
                DuckChatWebViewActivityWithParams(
                    url = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5",
                ),
            ),
        ).thenReturn(Intent())

        testee.openDuckChat()

        val intentCaptor = argumentCaptor<Intent>()
        verify(mockContext).startActivity(intentCaptor.capture())
        assertNull(intentCaptor.firstValue.component?.className)
    }

    @Test
    fun whenOpenDuckChatCalledWithQueryThenActivityStartedWithQuery() = runTest {
        testee.openDuckChatWithPrefill(query = "example")
        verify(mockGlobalActivityStarter).startIntent(
            mockContext,
            DuckChatWebViewActivityWithParams(
                url = "https://duckduckgo.com/?q=example&ia=chat&duckai=5",
            ),
        )
        verify(mockContext).startActivity(any())
        verify(mockDuckChatFeatureRepository).registerOpened()
    }

    @Test
    fun whenOpenDuckChatCalledWithBangQueryThenActivityStartedWithBangQuery() = runTest {
        duckChatFeature.self().setRawStoredState(State(enable = true, settings = SETTINGS_JSON))
        duckChatFeature.keepSession().setRawStoredState(State(enable = false))
        testee.onPrivacyConfigDownloaded()

        testee.openDuckChatWithPrefill(query = "example !ai")
        verify(mockGlobalActivityStarter).startIntent(
            mockContext,
            DuckChatWebViewActivityWithParams(
                url = "https://duckduckgo.com/?q=example&bang=true&ia=chat&duckai=5",
            ),
        )
        verify(mockContext).startActivity(any())
        verify(mockDuckChatFeatureRepository).registerOpened()
    }

    @Test
    fun whenOpenDuckChatCalledWithBangsButNoQueryThenActivityStartedWithoutPrompt() = runTest {
        duckChatFeature.self().setRawStoredState(State(enable = true, settings = SETTINGS_JSON))
        testee.onPrivacyConfigDownloaded()

        testee.openDuckChatWithPrefill(query = "!ai !image")
        verify(mockGlobalActivityStarter).startIntent(
            mockContext,
            DuckChatWebViewActivityWithParams(
                url = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5",
            ),
        )
        verify(mockContext).startActivity(any())
        verify(mockDuckChatFeatureRepository).registerOpened()
    }

    @Test
    fun whenIsDuckChatUrlCalledWithBangQueryThenReturnTrue() = runTest {
        duckChatFeature.self().setRawStoredState(State(enable = true, settings = SETTINGS_JSON))
        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.isDuckChatUrl(uri = "example !ai".toUri()))
    }

    @Test
    fun whenIsDuckChatUrlCalledWithBangQueryWithEmptyBangsThenReturnFalse() = runTest {
        duckChatFeature.self().setRawStoredState(State(enable = true, settings = SETTINGS_JSON_EMPTY_BANGS))
        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.isDuckChatUrl(uri = "example !ai".toUri()))
    }

    @Test
    fun whenIsDuckChatUrlCalledWithBangQueryWithNoBangsThenReturnFalse() = runTest {
        duckChatFeature.self().setRawStoredState(State(enable = true, settings = SETTINGS_JSON_NO_BANGS))
        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.isDuckChatUrl(uri = "example !ai".toUri()))
    }

    @Test
    fun whenIsDuckChatUrlCalledWithBangQueryWithNoRegexThenReturnFalse() = runTest {
        duckChatFeature.self().setRawStoredState(State(enable = true, settings = SETTINGS_JSON_NO_REGEX))
        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.isDuckChatUrl(uri = "example !ai".toUri()))
    }

    @Test
    fun whenOpenDuckChatCalledWithQueryAndAutoPromptThenActivityStartedWithQueryAndAutoPrompt() = runTest {
        testee.openDuckChatWithAutoPrompt(query = "example")
        verify(mockGlobalActivityStarter).startIntent(
            mockContext,
            DuckChatWebViewActivityWithParams(
                url = "https://duckduckgo.com/?q=example&prompt=1&ia=chat&duckai=5",
            ),
        )
        verify(mockContext).startActivity(any())
        verify(mockDuckChatFeatureRepository).registerOpened()
    }

    @Test
    fun whenIsDuckDuckGoHostAndDuckChatEnabledAndIsDuckChatLinkThenIsDuckChatUrl() {
        assertTrue(testee.isDuckChatUrl("https://duckduckgo.com/?ia=chat".toUri()))
    }

    @Test
    fun whenIsDuckDuckGoHostAndDuckChatEnabledAndIsNotDuckChatLinkThenIsNotDuckChatUrl() {
        assertFalse(testee.isDuckChatUrl("https://duckduckgo.com/?q=test".toUri()))
    }

    @Test
    fun whenIsNotDuckDuckGoHostAndDuckChatEnabledThenIsNotDuckChatUrl() {
        assertFalse(testee.isDuckChatUrl("https://example.com/?ia=chat".toUri()))
    }

    @Test
    fun whenWasOpenedBeforeQueriedThenRepoStateIsReturned() = runTest {
        whenever(mockDuckChatFeatureRepository.wasOpenedBefore()).thenReturn(true)

        assertTrue(testee.wasOpenedBefore())
    }

    @Test
    fun whenOpenDuckChatSettingsCalledThenGlobalActivityStarterCalledWithDuckChatSettings() = runTest {
        whenever(mockGlobalActivityStarter.startIntent(any(), any<ActivityParams>())).thenReturn(Intent())

        testee.openDuckChatSettings()

        verify(mockGlobalActivityStarter).startIntent(mockContext, DuckChatSettingsNoParams)

        val intentCaptor = argumentCaptor<Intent>()
        verify(mockContext).startActivity(intentCaptor.capture())
        val capturedIntent = intentCaptor.firstValue

        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, capturedIntent.flags)

        verify(testee).closeDuckChat()
    }

    @Test
    fun whenCloseDuckChatCalledThenOnCloseIsInvoked() = runTest {
        val testLifecycleOwner = TestLifecycleOwner(initialState = CREATED)

        var onCloseCalled = false
        testee.observeCloseEvent(testLifecycleOwner) {
            onCloseCalled = true
        }

        testLifecycleOwner.currentState = Lifecycle.State.STARTED

        testee.closeDuckChat()

        advanceUntilIdle()

        assertTrue(onCloseCalled)
    }

    @Test
    fun whenSetEnableDuckChatUserSettingTrueThenEnabledPixelSentAndRepositoryUpdated() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)

        testee.setEnableDuckChatUserSetting(true)

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_USER_ENABLED)
        verify(mockDuckChatFeatureRepository).setDuckChatUserEnabled(true)
        assertTrue(testee.isDuckChatUserEnabled())
    }

    @Test
    fun whenSetEnableDuckChatUserSettingFalseThenDisabledPixelSentAndRepositoryUpdated() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(false)

        testee.setEnableDuckChatUserSetting(false)

        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_USER_DISABLED)
        verify(mockDuckChatFeatureRepository).setDuckChatUserEnabled(false)
        assertFalse(testee.isDuckChatUserEnabled())
    }

    @Test
    fun whenObserveEnableDuckChatUserSettingThenEmitCorrectValues() = runTest {
        whenever(mockDuckChatFeatureRepository.observeDuckChatUserEnabled()).thenReturn(flowOf(true, false))

        val results = testee.observeEnableDuckChatUserSetting().take(2).toList()

        assertTrue(results[0])
        assertFalse(results[1])
    }

    @Test
    fun whenUserDisablesDuckChatAndSettingsAreTrueThenSettingsReturnFalse() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(false)
        whenever(mockDuckChatFeatureRepository.shouldShowInBrowserMenu()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(true)
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR,
            ),
        )
        testee.onPrivacyConfigDownloaded()
        testee.setEnableDuckChatUserSetting(false)

        verify(mockDuckChatFeatureRepository).setDuckChatUserEnabled(false)
        assertFalse(testee.showPopupMenuShortcut.value)
        assertFalse(testee.showOmnibarShortcutOnNtpAndOnFocus.value)
    }

    @Test
    fun whenUserEnablesDuckChatAndSettingsAreTrueThenSettingsReturnTrue() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInBrowserMenu()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(true)
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR,
            ),
        )
        testee.onPrivacyConfigDownloaded()
        testee.setEnableDuckChatUserSetting(true)

        verify(mockDuckChatFeatureRepository).setDuckChatUserEnabled(true)
        assertTrue(testee.showPopupMenuShortcut.value)
        assertTrue(testee.showOmnibarShortcutOnNtpAndOnFocus.value)
    }

    @Test
    fun whenUserEnablesDuckChatAndSettingsAreFalseThenSettingsReturnFalse() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInBrowserMenu()).thenReturn(false)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(false)
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR,
            ),
        )
        testee.setEnableDuckChatUserSetting(true)

        assertFalse(testee.showPopupMenuShortcut.value)
        assertFalse(testee.showOmnibarShortcutOnNtpAndOnFocus.value)
    }

    @Test
    fun whenAddressBarEntryPointDisabledThenShowInAddressBarReturnsFalse() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(true)
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR_ENTRY_POINT_DISABLED,
            ),
        )
        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.showOmnibarShortcutOnNtpAndOnFocus.value)
    }

    @Test
    fun whenAddressBarEntryPointEnabledThenShowInAddressBarReturnsTrue() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(true)
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR,
            ),
        )
        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.showOmnibarShortcutOnNtpAndOnFocus.value)
    }

    @Test
    fun `when should show in address bar enabled and duckAiButtonInBrowser enabled, then show in all states enabled`() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(true)
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR,
            ),
        )
        duckChatFeature.duckAiButtonInBrowser().setRawStoredState(State(enable = true))
        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.showOmnibarShortcutInAllStates.value)
    }

    @Test
    fun `when should show in address bar enabled and duckAiButtonInBrowser disabled, then show in all states disabled`() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(true)
        duckChatFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = SETTINGS_JSON_ADDRESS_BAR,
            ),
        )
        duckChatFeature.duckAiButtonInBrowser().setRawStoredState(State(enable = false))
        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.showOmnibarShortcutInAllStates.value)
    }

    @Test
    fun `when should show in address bar disabled and duckAiButtonInBrowser enabled, then show in all states enabled`() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(true)
        duckChatFeature.self().setRawStoredState(
            State(
                enable = false,
                settings = SETTINGS_JSON_ADDRESS_BAR,
            ),
        )
        duckChatFeature.duckAiButtonInBrowser().setRawStoredState(State(enable = true))
        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.showOmnibarShortcutInAllStates.value)
    }

    @Test
    fun whenUpdateChatStateThenChatStateUpdated() = runTest {
        assertEquals(ChatState.HIDE, testee.chatState.value)

        val newState = ChatState.LOADING
        testee.updateChatState(newState)

        assertEquals(newState, testee.chatState.value)
    }

    @Test
    fun whenUpdateChatStateThenFlowEmitsInitialAndNewState() = runTest {
        val emissions = mutableListOf<ChatState>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            testee.chatState
                .take(2)
                .toList(emissions)
        }
        testee.updateChatState(ChatState.READY)
        advanceUntilIdle()

        assertEquals(listOf(ChatState.HIDE, ChatState.READY), emissions)
        collectJob.cancel()
    }

    @Test
    fun whenImageUploadFeatureDisabledThenDisableImageUpload() = runTest {
        imageUploadFeature.self().setRawStoredState(State(enable = false))

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.isImageUploadEnabled())
    }

    @Test
    fun `when enable input screen user setting then repository updated`() = runTest {
        testee.setInputScreenUserSetting(true)

        val inOrder = inOrder(mockDuckChatFeatureRepository)
        inOrder.verify(mockDuckChatFeatureRepository).setInputScreenUserSetting(true)
        inOrder.verify(mockDuckChatFeatureRepository).isInputScreenUserSettingEnabled()
    }

    @Test
    fun `when disable input screen user setting then repository updated`() = runTest {
        testee.setInputScreenUserSetting(false)

        val inOrder = inOrder(mockDuckChatFeatureRepository)
        inOrder.verify(mockDuckChatFeatureRepository).setInputScreenUserSetting(false)
        inOrder.verify(mockDuckChatFeatureRepository).isInputScreenUserSettingEnabled()
    }

    @Test
    fun `when observe input screen user setting then emit correct values`() = runTest {
        whenever(mockDuckChatFeatureRepository.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true, false))

        val results = testee.observeInputScreenUserSettingEnabled().take(2).toList()
        assertTrue(results[0])
        assertFalse(results[1])
    }

    @Test
    fun `input screen feature - when enabled then emit enabled`() = runTest {
        assertTrue(testee.showInputScreen.value)
    }

    @Test
    fun `input screen feature - when global feature flag disabled then emit disabled`() = runTest {
        duckChatFeature.self().setRawStoredState(State(false))

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.showInputScreen.value)
    }

    @Test
    fun `input screen feature - when input screen feature flag disabled then emit disabled`() = runTest {
        duckChatFeature.duckAiInputScreen().setRawStoredState(State(false))

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.showInputScreen.value)
    }

    @Test
    fun `input screen feature - when visual design experiment disabled then emit disabled`() = runTest {
        isVisualDesignEnabledStateFlow.value = false

        verify(mockDuckChatFeatureRepository).setInputScreenUserSetting(false)
        assertFalse(testee.showInputScreen.value)
    }

    @Test
    fun `input screen feature - when available, return correct value`() = runTest {
        assertTrue(testee.isInputScreenFeatureAvailable())

        duckChatFeature.duckAiInputScreen().setRawStoredState(State(false))

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.isInputScreenFeatureAvailable())

        duckChatFeature.duckAiInputScreen().setRawStoredState(State(true))

        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.isInputScreenFeatureAvailable())
    }

    @Test
    fun `when keep session feature flag disabled then session is not kept alive`() = runTest {
        duckChatFeature.keepSession().setRawStoredState(State(false))

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.keepSession.value)
    }

    @Test
    fun `when keep session feature flag enabled then session is kept alive`() = runTest {
        duckChatFeature.keepSession().setRawStoredState(State(true))

        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.keepSession.value)
    }

    @Test
    fun `when global feature flag disabled then don't show settings`() = runTest {
        duckChatFeature.self().setRawStoredState(State(false))

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.showSettings.value)
    }

    @Test
    fun `when global feature flag enabled then show settings`() = runTest {
        duckChatFeature.self().setRawStoredState(State(true))

        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.showSettings.value)
    }

    companion object {
        val SETTINGS_JSON = """
        {
            "aiChatBangs": ["!ai", "!aichat", "!chat", "!duckai"],
            "aiChatBangRegex": "^(?!({bangs})${'$'})(?=.*({bangs})(?=${'$'}|\\s)).+${'$'}"
        }
        """.trimIndent()

        val SETTINGS_JSON_EMPTY_BANGS = """
        {
            "aiChatBangs": [],
            "aiChatBangRegex": "^(?!({bangs})${'$'})(?=.*({bangs})(?=${'$'}|\\s)).+${'$'}"
        }
        """.trimIndent()

        val SETTINGS_JSON_NO_BANGS = """
        {
            "aiChatBangRegex": "^(?!({bangs})${'$'})(?=.*({bangs})(?=${'$'}|\\s)).+${'$'}"
        }
        """.trimIndent()

        val SETTINGS_JSON_NO_REGEX = """
        {
            "aiChatBangs": ["!ai", "!aichat", "!chat", "!duckai"],
        }
        """.trimIndent()

        val SETTINGS_JSON_ADDRESS_BAR = """
        {
            "addressBarEntryPoint": true,
            "addressBarAnimation": true,
            "addressBarChangeBoundsDuration": 123,
            "addressBarFadeDuration": 456,
            "addressBarTension": 7.8
        }
        """.trimIndent()

        val SETTINGS_JSON_ADDRESS_BAR_ENTRY_POINT_DISABLED = """
        {
            "addressBarEntryPoint": false,
            "addressBarAnimation": true,
            "addressBarChangeBoundsDuration": 123,
            "addressBarFadeDuration": 456,
            "addressBarTension": 7.8
        }
        """.trimIndent()
    }
}
