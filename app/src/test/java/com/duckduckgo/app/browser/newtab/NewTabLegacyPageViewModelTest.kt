/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.newtab

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command
import com.duckduckgo.app.browser.remotemessage.CommandActionMapper
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId.DAX_END
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.R
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.engine.SyncEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NewTabLegacyPageViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private var mockLifecycleOwner: LifecycleOwner = mock()
    private var mockSavedSitesRepository: SavedSitesRepository = mock()
    private var mockSyncEngine: SyncEngine = mock()
    private var mockCommandActionMapper: CommandActionMapper = mock()
    private var mockPlaystoreUtils: PlayStoreUtils = mock()
    private var mockRemoteMessageModel: RemoteMessageModel = mock()
    private var mockDismissedCtaDao: DismissedCtaDao = mock()
    private val mockExtendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockLowPriorityMessagingModel: LowPriorityMessagingModel = mock()

    private lateinit var testee: NewTabLegacyPageViewModel

    @Before
    fun setUp() {
        val mockDisabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(null))

        testee = NewTabLegacyPageViewModel(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            remoteMessagingModel = mockRemoteMessageModel,
            playStoreUtils = mockPlaystoreUtils,
            savedSitesRepository = mockSavedSitesRepository,
            syncEngine = mockSyncEngine,
            commandActionMapper = mockCommandActionMapper,
            dismissedCtaDao = mockDismissedCtaDao,
            extendedOnboardingFeatureToggles = mockExtendedOnboardingFeatureToggles,
            settingsDataStore = mockSettingsDataStore,
            lowPriorityMessagingModel = mockLowPriorityMessagingModel,
        )
    }

    @Test
    fun whenViewModelIsInitializedThenViewStateShouldEmitInitialState() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(false)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertEquals(it.message, remoteMessage)
                assertTrue(it.favourites.isEmpty())
                assertTrue(it.newMessage)
                assertFalse(it.onboardingComplete)
            }
        }
    }

    @Test
    fun whenRemoteMessageAvailableAndOnboardingNotCompleteThenMessageNotShown() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(false)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertEquals(it.message, remoteMessage)
                assertTrue(it.favourites.isEmpty())
                assertTrue(it.newMessage)
                assertFalse(it.onboardingComplete)
            }
        }
    }

    @Test
    fun whenRemoteMessageAvailableAndOnboardingCompleteThenMessageShown() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertEquals(it.message, remoteMessage)
                assertTrue(it.favourites.isEmpty())
                assertTrue(it.newMessage)
                assertTrue(it.onboardingComplete)
            }
        }
    }

    @Test
    fun whenRemoteMessageShownThenFirePixelAndMarkAsShown() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        testee.onStart(mockLifecycleOwner)

        testee.onMessageShown()

        verify(mockRemoteMessageModel).onMessageShown(remoteMessage)
    }

    @Test
    fun whenRemoteMessageCloseButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        testee.onStart(mockLifecycleOwner)

        testee.onMessageCloseButtonClicked()

        verify(mockRemoteMessageModel).onMessageDismissed(remoteMessage)
    }

    @Test
    fun whenRemoteMessagePrimaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        val action = Action.Dismiss
        whenever(mockRemoteMessageModel.onPrimaryActionClicked(remoteMessage)).thenReturn(Action.Dismiss)
        whenever(mockCommandActionMapper.asNewTabCommand(action)).thenReturn(NewTabLegacyPageViewModel.Command.DismissMessage)

        testee.onStart(mockLifecycleOwner)

        testee.onMessagePrimaryButtonClicked()

        testee.commands().test {
            expectMostRecentItem().also {
                assertEquals(it, Command.DismissMessage)
            }
        }
    }

    @Test
    fun whenRemoteMessageSecondaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        val action = Action.Dismiss
        whenever(mockRemoteMessageModel.onSecondaryActionClicked(remoteMessage)).thenReturn(Action.Dismiss)
        whenever(mockCommandActionMapper.asNewTabCommand(action)).thenReturn(NewTabLegacyPageViewModel.Command.DismissMessage)

        testee.onStart(mockLifecycleOwner)

        testee.onMessageSecondaryButtonClicked()

        testee.commands().test {
            expectMostRecentItem().also {
                assertEquals(it, Command.DismissMessage)
            }
        }
    }

    @Test
    fun whenRemoteMessageActionButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        val action = Action.Dismiss
        whenever(mockRemoteMessageModel.onActionClicked(remoteMessage)).thenReturn(Action.Dismiss)
        whenever(mockCommandActionMapper.asNewTabCommand(action)).thenReturn(NewTabLegacyPageViewModel.Command.DismissMessage)

        testee.onStart(mockLifecycleOwner)

        testee.onMessageActionButtonClicked()

        testee.commands().test {
            expectMostRecentItem().also {
                assertEquals(it, Command.DismissMessage)
            }
        }
    }

    @Test
    fun whenOnLowPriorityMessagePrimaryButtonClickedThenGetPrimaryButtonCommandCalledAndCommandSent() = runTest {
        whenever(mockLowPriorityMessagingModel.getPrimaryButtonCommand()).thenReturn(Command.LaunchDefaultBrowser)
        testee.onLowPriorityMessagePrimaryButtonClicked()

        verify(mockLowPriorityMessagingModel).getPrimaryButtonCommand()
        testee.commands().test {
            expectMostRecentItem().also {
                assertEquals(it, Command.LaunchDefaultBrowser)
            }
        }
    }

    @Test
    fun whenRemoteMessageAvailableAndLowPriorityMessageAvailableThenLowPriorityMessageIsNull() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        val lowPriorityMessage = LowPriorityMessage.DefaultBrowserMessage(
            message = MessageCta.Message(
                topIllustration = R.drawable.ic_device_mobile_default,
                title = "Set as default browser",
                action = "Set as default",
                action2 = "Do not ask again",
            ),
            onPrimaryAction = {},
            onSecondaryAction = {},
            onClose = {},
            onShown = {},
        )
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)
        whenever(mockLowPriorityMessagingModel.getMessage()).thenReturn(lowPriorityMessage)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertEquals(remoteMessage, it.message)
                assertTrue(it.newMessage)
                assertNull(it.lowPriorityMessage)
            }
        }
    }

    @Test
    fun whenNoRemoteMessageAvailableAndLowPriorityMessageAvailableThenLowPriorityMessageIsShown() = runTest {
        val remoteMessage: RemoteMessage? = null
        val lowPriorityMessage = LowPriorityMessage.DefaultBrowserMessage(
            message = MessageCta.Message(
                topIllustration = R.drawable.ic_device_mobile_default,
                title = "Set as default browser",
                action = "Set as default",
                action2 = "Do not ask again",
            ),
            onPrimaryAction = {},
            onSecondaryAction = {},
            onClose = {},
            onShown = {},
        )
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)
        whenever(mockLowPriorityMessagingModel.getMessage()).thenReturn(lowPriorityMessage)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertNull(it.message)
                assertFalse(it.newMessage)
                assertEquals(lowPriorityMessage, it.lowPriorityMessage)
            }
        }
    }
}
