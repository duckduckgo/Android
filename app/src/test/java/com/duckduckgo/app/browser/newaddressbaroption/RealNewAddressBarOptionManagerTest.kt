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

import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealNewAddressBarOptionManagerTest {

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
    fun setUp() = runTest {
        MockitoAnnotations.openMocks(this)

        whenever(duckAiFeatureStateMock.showNewAddressBarOptionAnnouncement).thenReturn(showNewAddressBarOptionAnnouncementFlow)
        whenever(duckAiFeatureStateMock.showOmnibarShortcutInAllStates).thenReturn(showOmnibarShortcutInAllStatesFlow)
        whenever(duckAiFeatureStateMock.showInputScreen).thenReturn(showInputScreenFlow)

        testee = RealNewAddressBarOptionManager(
            duckAiFeatureStateMock,
            userStageStoreMock,
            duckChatMock,
            remoteMessagingRepositoryMock,
            newAddressBarOptionDataStoreMock,
            settingsDataStoreMock,
        )
    }

    @Test
    fun `when all conditions are met and not launched from external then should trigger returns true`() = runTest {
        setupAllConditionsMet()

        assertTrue(testee.shouldTrigger(launchedFromExternal = false))
    }

    @Test
    fun `when launched from external then should trigger returns false`() = runTest {
        setupAllConditionsMet()

        assertFalse(testee.shouldTrigger(launchedFromExternal = true))
    }

    @Test
    fun `when duck AI is disabled then should trigger returns false`() = runTest {
        setupAllConditionsMet()
        whenever(duckChatMock.isEnabled()).thenReturn(false)

        assertFalse(testee.shouldTrigger(launchedFromExternal = false))
    }

    @Test
    fun `when onboarding is not completed then should trigger returns false`() = runTest {
        setupAllConditionsMet()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)

        assertFalse(testee.shouldTrigger(launchedFromExternal = false))
    }

    @Test
    fun `when feature flag is disabled then should trigger returns false`() = runTest {
        setupAllConditionsMet()
        showNewAddressBarOptionAnnouncementFlow.value = false

        assertFalse(testee.shouldTrigger(launchedFromExternal = false))
    }

    @Test
    fun `when duck AI omnibar shortcut is disabled then should trigger returns false`() = runTest {
        setupAllConditionsMet()
        showOmnibarShortcutInAllStatesFlow.value = false

        assertFalse(testee.shouldTrigger(launchedFromExternal = false))
    }

    @Test
    fun `when input screen is enabled then should trigger returns false`() = runTest {
        setupAllConditionsMet()
        showInputScreenFlow.value = true

        assertFalse(testee.shouldTrigger(launchedFromExternal = false))
    }

    @Test
    fun `when force choice has been shown then should trigger returns false`() = runTest {
        setupAllConditionsMet()
        whenever(newAddressBarOptionDataStoreMock.getHasBeenShown()).thenReturn(true)

        assertFalse(testee.shouldTrigger(launchedFromExternal = false))
    }

    @Test
    fun `when user has interacted with search and duck AI announcement then should trigger returns false`() = runTest {
        setupAllConditionsMet()
        whenever(remoteMessagingRepositoryMock.dismissedMessages()).thenReturn(listOf("search_duck_ai_announcement"))

        assertFalse(testee.shouldTrigger(launchedFromExternal = false))
    }

    @Test
    fun `when bottom address bar is enabled then should trigger returns false`() = runTest {
        setupAllConditionsMet()
        whenever(settingsDataStoreMock.omnibarPosition).thenReturn(OmnibarPosition.BOTTOM)

        assertFalse(testee.shouldTrigger(launchedFromExternal = false))
    }

    @Test
    fun `when mark as shown is called then data store mark as shown is called`() = runTest {
        testee.markAsShown()

        verify(newAddressBarOptionDataStoreMock).markAsShown()
    }

    private suspend fun setupAllConditionsMet() {
        whenever(duckChatMock.isEnabled()).thenReturn(true)
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        showNewAddressBarOptionAnnouncementFlow.value = true
        showOmnibarShortcutInAllStatesFlow.value = true
        showInputScreenFlow.value = false
        whenever(newAddressBarOptionDataStoreMock.getHasBeenShown()).thenReturn(false)
        whenever(remoteMessagingRepositoryMock.dismissedMessages()).thenReturn(emptyList())
        whenever(settingsDataStoreMock.omnibarPosition).thenReturn(OmnibarPosition.TOP)
    }
}
