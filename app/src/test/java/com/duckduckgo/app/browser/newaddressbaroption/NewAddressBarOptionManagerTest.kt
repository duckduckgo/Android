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

import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private var remoteMessagingRepositoryMock: RemoteMessagingRepository = mock()

    @Mock
    private var newAddressBarOptionDataStoreMock: NewAddressBarOptionDataStore = mock()

    @Mock
    private var settingsDataStoreMock: SettingsDataStore = mock()

    private val showNewAddressBarOptionAnnouncementFlow = MutableStateFlow(false)
    private val showOmnibarShortcutInAllStatesFlow = MutableStateFlow(false)
    private val showInputScreenFlow = MutableStateFlow(false)

    private lateinit var testee: RealNewAddressBarOptionManager

    @Before
    fun setUp() =
        runTest {
            MockitoAnnotations.openMocks(this)

            whenever(duckAiFeatureStateMock.showNewAddressBarOptionChoiceScreen).thenReturn(showNewAddressBarOptionAnnouncementFlow)
            whenever(duckAiFeatureStateMock.showOmnibarShortcutInAllStates).thenReturn(showOmnibarShortcutInAllStatesFlow)
            whenever(duckAiFeatureStateMock.showInputScreen).thenReturn(showInputScreenFlow)

            testee =
                RealNewAddressBarOptionManager(
                    duckAiFeatureStateMock,
                    userStageStoreMock,
                    duckChatMock,
                    remoteMessagingRepositoryMock,
                    newAddressBarOptionDataStoreMock,
                    settingsDataStoreMock,
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
            whenever(remoteMessagingRepositoryMock.dismissedMessages()).thenReturn(listOf("search_duck_ai_announcement"))

            testee.showChoiceScreen(mock(), isLaunchedFromExternal = false)

            verify(duckChatMock, never()).showNewAddressBarOptionChoiceScreen(any(), any())
        }

    @Test
    fun `when bottom address bar is enabled then showChoiceScreen does not show dialog`() =
        runTest {
            setupAllConditionsMet()
            whenever(settingsDataStoreMock.omnibarPosition).thenReturn(OmnibarPosition.BOTTOM)

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

    private suspend fun setupAllConditionsMet() {
        whenever(duckChatMock.isEnabled()).thenReturn(true)
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        showNewAddressBarOptionAnnouncementFlow.value = true
        showOmnibarShortcutInAllStatesFlow.value = true
        showInputScreenFlow.value = false
        whenever(newAddressBarOptionDataStoreMock.wasShown()).thenReturn(false)
        whenever(remoteMessagingRepositoryMock.dismissedMessages()).thenReturn(emptyList())
        whenever(settingsDataStoreMock.omnibarPosition).thenReturn(OmnibarPosition.TOP)
        whenever(newAddressBarOptionDataStoreMock.wasValidated()).thenReturn(true)
    }
}
