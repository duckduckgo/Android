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

package com.duckduckgo.app.browser.newaddressbaroption

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarSelection
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NewAddressBarOptionManagerTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private var duckAiFeatureStateMock: DuckAiFeatureState = mock()

    @Mock
    private var userStageStoreMock: UserStageStore = mock()

    @Mock
    private var duckChatMock: DuckChat = mock()

    @Mock
    private var remoteMessageModelMock: RemoteMessageModel = mock()

    @Mock
    private var newAddressBarOptionDataStoreMock: NewAddressBarOptionDataStore = mock()

    @Mock
    private var settingsDataStoreMock: SettingsDataStore = mock()

    @Mock
    private var onboardingStoreMock: OnboardingStore = mock()

    @Mock
    private var pixelMock: Pixel = mock()

    @Mock
    private var appThemeMock: AppTheme = mock()

    @Mock
    private var v2DialogFactoryMock: NewAddressBarOptionV2BottomSheetDialogFactory = mock()

    private val v2DialogMock: BottomSheetDialog = mock()

    private val showNewAddressBarOptionAnnouncementFlow = MutableStateFlow(false)
    private val showNewAddressBarOptionChoiceScreenV2Flow = MutableStateFlow(false)
    private val showOmnibarShortcutInAllStatesFlow = MutableStateFlow(false)
    private val showInputScreenFlow = MutableStateFlow(false)

    private lateinit var testee: RealNewAddressBarOptionManager

    @Before
    fun setUp() =
        runTest {
            MockitoAnnotations.openMocks(this)

            whenever(duckAiFeatureStateMock.showNewAddressBarOptionChoiceScreen).thenReturn(showNewAddressBarOptionAnnouncementFlow)
            whenever(duckAiFeatureStateMock.showNewAddressBarOptionChoiceScreenV2).thenReturn(showNewAddressBarOptionChoiceScreenV2Flow)
            whenever(duckAiFeatureStateMock.showOmnibarShortcutInAllStates).thenReturn(showOmnibarShortcutInAllStatesFlow)
            whenever(duckAiFeatureStateMock.showInputScreen).thenReturn(showInputScreenFlow)
            whenever(v2DialogFactoryMock.create(any(), any(), any())).thenReturn(v2DialogMock)
            whenever(appThemeMock.isLightModeEnabled()).thenReturn(true)

            testee =
                RealNewAddressBarOptionManager(
                    duckAiFeatureStateMock,
                    userStageStoreMock,
                    duckChatMock,
                    remoteMessageModelMock,
                    newAddressBarOptionDataStoreMock,
                    settingsDataStoreMock,
                    onboardingStoreMock,
                    v2DialogFactoryMock,
                    pixelMock,
                    appThemeMock,
                    coroutineTestRule.testScope,
                    coroutineTestRule.testDispatcherProvider,
                )
        }

    @Test
    fun `when all conditions are met and not launched from external then showChoiceScreen shows dialog`() =
        runTest {
            setupAllConditionsMet()

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when launched from external then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = true)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when duck AI is disabled then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            whenever(duckChatMock.isEnabled()).thenReturn(false)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when onboarding is not completed then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when feature flag is disabled then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionAnnouncementFlow.value = false

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when duck AI omnibar shortcut is disabled then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            showOmnibarShortcutInAllStatesFlow.value = false

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when input screen is enabled then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            showInputScreenFlow.value = true

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when new address bar option was shown before then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            whenever(newAddressBarOptionDataStoreMock.wasShown()).thenReturn(true)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when user has interacted with search and duck AI announcement then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            whenever(remoteMessageModelMock.isMessageDismissed("search_duck_ai_announcement")).thenReturn(true)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when bottom address bar is enabled then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            whenever(settingsDataStoreMock.omnibarType).thenReturn(OmnibarType.SINGLE_BOTTOM)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when was not validated before then showChoiceScreen does not show dialog and sets as validated`() =
        runTest {
            setupAllConditionsMet()
            whenever(newAddressBarOptionDataStoreMock.wasValidated()).thenReturn(false)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
            verify(newAddressBarOptionDataStoreMock).setAsValidated()
        }

    @Test
    fun `when was validated before then showChoiceScreen shows dialog`() =
        runTest {
            setupAllConditionsMet()

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when feature is disabled then setAsValidated is not called`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionAnnouncementFlow.value = false
            whenever(newAddressBarOptionDataStoreMock.wasValidated()).thenReturn(false)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(newAddressBarOptionDataStoreMock, never()).setAsValidated()
            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when activity is finishing then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            val mockActivity = mock<DuckDuckGoActivity>()
            whenever(mockActivity.isFinishing).thenReturn(true)
            whenever(mockActivity.isDestroyed).thenReturn(false)

            testee.showChoiceScreen(mockActivity, isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when activity is destroyed then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            val mockActivity = mock<DuckDuckGoActivity>()
            whenever(mockActivity.isFinishing).thenReturn(false)
            whenever(mockActivity.isDestroyed).thenReturn(true)

            testee.showChoiceScreen(mockActivity, isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when dialog is shown then setAsShown is called`() =
        runTest {
            setupAllConditionsMet()

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock).showNewAddressBarOptionChoiceScreen(any(), any())
            verify(newAddressBarOptionDataStoreMock).setAsShown()
        }

    @Test
    fun `when dialog is shown then dark theme parameter is passed correctly`() =
        runTest {
            setupAllConditionsMet()
            val mockActivity = mock<DuckDuckGoActivity>()
            whenever(mockActivity.isDarkThemeEnabled()).thenReturn(true)

            testee.showChoiceScreen(mockActivity, isLaunchedFromExternal = false)

            val activityCaptor = argumentCaptor<DuckDuckGoActivity>()
            val darkThemeCaptor = argumentCaptor<Boolean>()
            verify(duckChatMock).showNewAddressBarOptionChoiceScreen(activityCaptor.capture(), darkThemeCaptor.capture())

            assertEquals(mockActivity, activityCaptor.firstValue)
            assertEquals(true, darkThemeCaptor.firstValue)
        }

    @Test
    fun `when setAsShown is called then data store setAsShown is called`() =
        runTest {
            testee.setAsShown()

            verify(newAddressBarOptionDataStoreMock).setAsShown()
        }

    @Test
    fun `when input screen selection exists then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            whenever(onboardingStoreMock.getInputScreenSelection()).thenReturn(true)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when input screen selection is false then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            whenever(onboardingStoreMock.getInputScreenSelection()).thenReturn(false)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when v2 enabled and all conditions met then shows v2 dialog and sets as shown v2`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(v2DialogFactoryMock).create(any(), any(), any())
            verify(v2DialogMock).show()
            verify(newAddressBarOptionDataStoreMock).setAsShownV2()
            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when v2 enabled and v1 seen but v2 not seen then shows v2 dialog`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true
            whenever(newAddressBarOptionDataStoreMock.wasShown()).thenReturn(true)
            whenever(newAddressBarOptionDataStoreMock.wasShownV2()).thenReturn(false)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(v2DialogFactoryMock).create(any(), any(), any())
        }

    @Test
    fun `when v2 enabled and v2 already shown then does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true
            whenever(newAddressBarOptionDataStoreMock.wasShownV2()).thenReturn(true)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(v2DialogFactoryMock, never()).create(any(), any(), any())
        }

    @Test
    fun `when v2 enabled and input screen was ever enabled then does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true
            whenever(duckChatMock.isInputScreenEverEnabled()).thenReturn(true)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(v2DialogFactoryMock, never()).create(any(), any(), any())
        }

    @Test
    fun `when v2 enabled and bottom address bar selected then still shows v2 dialog`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true
            whenever(settingsDataStoreMock.omnibarType).thenReturn(OmnibarType.SINGLE_BOTTOM)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(v2DialogFactoryMock).create(any(), any(), any())
        }

    @Test
    fun `when v2 enabled and omnibar shortcut disabled then still shows v2 dialog`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true
            showOmnibarShortcutInAllStatesFlow.value = false

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(v2DialogFactoryMock).create(any(), any(), any())
        }

    @Test
    fun `when v2 enabled and onboarding not completed then still shows v2 dialog`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true
            whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(v2DialogFactoryMock).create(any(), any(), any())
        }

    @Test
    fun `when v2 enabled and launched from external then still shows v2 dialog`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = true)

            verify(v2DialogFactoryMock).create(any(), any(), any())
        }

    @Test
    fun `when v2 disabled and v1 enabled then shows v1 dialog and sets as shown`() =
        runTest {
            setupAllConditionsMet()

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock).showNewAddressBarOptionChoiceScreen(any(), any())
            verify(newAddressBarOptionDataStoreMock).setAsShown()
            verify(v2DialogFactoryMock, never()).create(any(), any(), any())
        }

    @Test
    fun `when v2 dialog displayed then fires v2 displayed pixel`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true
            var capturedCallback: NewAddressBarV2Callback? = null
            whenever(v2DialogFactoryMock.create(any(), any(), any())).thenAnswer { invocation ->
                capturedCallback = invocation.getArgument<NewAddressBarV2Callback?>(2)
                v2DialogMock
            }

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            assertNotNull(capturedCallback)
            capturedCallback!!.onDisplayed()
            verify(pixelMock).fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_DISPLAYED)
        }

    @Test
    fun `when v2 confirmed with search and ai then enables toggle and fires confirmed pixel`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true
            var capturedCallback: NewAddressBarV2Callback? = null
            whenever(v2DialogFactoryMock.create(any(), any(), any())).thenAnswer { invocation ->
                capturedCallback = invocation.getArgument<NewAddressBarV2Callback?>(2)
                v2DialogMock
            }

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            assertNotNull(capturedCallback)
            capturedCallback!!.onConfirmed(NewAddressBarSelection.SEARCH_AND_AI)
            coroutineTestRule.testScope.advanceUntilIdle()

            verify(duckChatMock).setInputScreenUserSetting(true)
            verify(pixelMock).fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED, mapOf("selection" to "search_and_ai"))
        }

    @Test
    fun `when v2 confirmed with search only then does not enable toggle and fires confirmed pixel`() =
        runTest {
            setupAllConditionsMet()
            showNewAddressBarOptionChoiceScreenV2Flow.value = true
            var capturedCallback: NewAddressBarV2Callback? = null
            whenever(v2DialogFactoryMock.create(any(), any(), any())).thenAnswer { invocation ->
                capturedCallback = invocation.getArgument<NewAddressBarV2Callback?>(2)
                v2DialogMock
            }

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            assertNotNull(capturedCallback)
            capturedCallback!!.onConfirmed(NewAddressBarSelection.SEARCH_ONLY)
            coroutineTestRule.testScope.advanceUntilIdle()

            verify(duckChatMock, never()).setInputScreenUserSetting(any())
            verify(pixelMock).fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED, mapOf("selection" to "search_only"))
        }

    private suspend fun setupAllConditionsMet() {
        whenever(duckChatMock.isEnabled()).thenReturn(true)
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        showNewAddressBarOptionAnnouncementFlow.value = true
        showOmnibarShortcutInAllStatesFlow.value = true
        showInputScreenFlow.value = false
        whenever(newAddressBarOptionDataStoreMock.wasShown()).thenReturn(false)
        whenever(newAddressBarOptionDataStoreMock.wasShownV2()).thenReturn(false)
        whenever(duckChatMock.isInputScreenEverEnabled()).thenReturn(false)
        whenever(remoteMessageModelMock.isMessageDismissed("search_duck_ai_announcement")).thenReturn(false)
        whenever(settingsDataStoreMock.omnibarType).thenReturn(OmnibarType.SINGLE_TOP)
        whenever(newAddressBarOptionDataStoreMock.wasValidated()).thenReturn(true)
        whenever(onboardingStoreMock.getInputScreenSelection()).thenReturn(null)
    }
}
