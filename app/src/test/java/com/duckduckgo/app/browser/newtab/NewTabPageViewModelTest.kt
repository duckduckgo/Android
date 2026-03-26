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
import com.duckduckgo.app.browser.newtab.NewTabPageViewModel.Command
import com.duckduckgo.app.browser.remotemessage.CommandActionMapper
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId.DAX_END
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.pixels.RemoteMessagingPixelName
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.sync.api.engine.SyncEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NewTabPageViewModelTest {

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
    private val mockAppTrackingProtection: AppTrackingProtection = mock()
    private val pixel: Pixel = mock()

    private lateinit var testee: NewTabPageViewModel

    @Before
    fun setUp() = runTest {
        val mockDisabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(null))
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(false)

        testee = createTestee()
    }

    private fun createTestee(showLogo: Boolean = true): NewTabPageViewModel {
        return NewTabPageViewModel(
            showDaxLogo = showLogo,
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
            appTrackingProtection = mockAppTrackingProtection,
            pixel = pixel,
        )
    }

    @Test
    fun whenViewModelIsInitializedThenViewStateShouldEmitInitialState() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(false)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertEquals(it.message, remoteMessage)
                assertTrue(it.favourites?.isEmpty() == true)
                assertTrue(it.newMessage)
                assertFalse(it.onboardingComplete)
            }
        }
    }

    @Test
    fun whenRemoteMessageAvailableAndOnboardingNotCompleteThenMessageNotShown() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(false)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertEquals(it.message, remoteMessage)
                assertTrue(it.favourites?.isEmpty() == true)
                assertTrue(it.newMessage)
                assertFalse(it.onboardingComplete)
            }
        }
    }

    @Test
    fun whenRemoteMessageAvailableAndOnboardingCompleteThenMessageShown() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertEquals(it.message, remoteMessage)
                assertTrue(it.favourites?.isEmpty() == true)
                assertTrue(it.newMessage)
                assertTrue(it.onboardingComplete)
                assertNull(it.messageImageFilePath)
            }
        }
    }

    @Test
    fun whenRemoteMessageAvailableWithStoredImageAndOnboardingCompleteThenMessageShown() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockRemoteMessageModel.getRemoteMessageImageFile(Surface.NEW_TAB_PAGE)).thenReturn("messageFile")
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertEquals(it.message, remoteMessage)
                assertEquals("messageFile", it.messageImageFilePath)
                assertTrue(it.favourites?.isEmpty() == true)
                assertTrue(it.newMessage)
                assertTrue(it.onboardingComplete)
            }
        }
    }

    @Test
    fun whenRemoteMessageShownThenFirePixelAndMarkAsShown() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        testee.onStart(mockLifecycleOwner)

        testee.onMessageShown()

        verify(mockRemoteMessageModel).onMessageShown(remoteMessage)
    }

    @Test
    fun whenRemoteMessageCloseButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        testee.onStart(mockLifecycleOwner)

        testee.onMessageCloseButtonClicked()

        verify(mockRemoteMessageModel).onMessageDismissed(remoteMessage)
        verify(mockRemoteMessageModel).clearMessageImage(Surface.NEW_TAB_PAGE)
    }

    @Test
    fun whenRemoteMessagePrimaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        val action = Action.Dismiss
        whenever(mockRemoteMessageModel.onPrimaryActionClicked(remoteMessage)).thenReturn(Action.Dismiss)
        whenever(mockCommandActionMapper.asNewTabCommand(action)).thenReturn(NewTabPageViewModel.Command.DismissMessage)

        testee.onStart(mockLifecycleOwner)

        testee.onMessagePrimaryButtonClicked()

        verify(mockRemoteMessageModel).clearMessageImage(Surface.NEW_TAB_PAGE)
        testee.commands().test {
            expectMostRecentItem().also {
                assertEquals(it, Command.DismissMessage)
            }
        }
    }

    @Test
    fun whenRemoteMessageSecondaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        val action = Action.Dismiss
        whenever(mockRemoteMessageModel.onSecondaryActionClicked(remoteMessage)).thenReturn(Action.Dismiss)
        whenever(mockCommandActionMapper.asNewTabCommand(action)).thenReturn(NewTabPageViewModel.Command.DismissMessage)

        testee.onStart(mockLifecycleOwner)

        testee.onMessageSecondaryButtonClicked()

        verify(mockRemoteMessageModel).clearMessageImage(Surface.NEW_TAB_PAGE)
        testee.commands().test {
            expectMostRecentItem().also {
                assertEquals(it, Command.DismissMessage)
            }
        }
    }

    @Test
    fun whenRemoteMessageActionButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        val action = Action.Dismiss
        whenever(mockRemoteMessageModel.onActionClicked(remoteMessage)).thenReturn(Action.Dismiss)
        whenever(mockCommandActionMapper.asNewTabCommand(action)).thenReturn(NewTabPageViewModel.Command.DismissMessage)

        testee.onStart(mockLifecycleOwner)

        testee.onMessageActionButtonClicked()

        verify(mockRemoteMessageModel).clearMessageImage(Surface.NEW_TAB_PAGE)
        testee.commands().test {
            expectMostRecentItem().also {
                assertEquals(it, Command.DismissMessage)
            }
        }
    }

    @Test
    fun whenRemoteMessageActionButtonClickedWithShareActionThenImageNotCleared() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        val shareAction = Action.Share("https://example.com", mapOf("title" to "Share Title"))
        whenever(mockRemoteMessageModel.onActionClicked(remoteMessage)).thenReturn(shareAction)
        whenever(mockCommandActionMapper.asNewTabCommand(shareAction)).thenReturn(
            NewTabPageViewModel.Command.SharePromoLinkRMF("https://example.com", "Share Title"),
        )

        testee.onStart(mockLifecycleOwner)

        testee.onMessageActionButtonClicked()

        verify(mockRemoteMessageModel, never()).clearMessageImage(any())
        testee.commands().test {
            val command = expectMostRecentItem()
            assertTrue(command is NewTabPageViewModel.Command.SharePromoLinkRMF)
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
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
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

    @Test
    fun `when onboarding finished and logo enabled, then show logo`() = runTest {
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.shouldShowLogo)
                assertTrue(it.hasContent)
            }
        }
    }

    @Test
    fun `when onboarding finished and logo disabled, then hide logo and report no content`() = runTest {
        val testeeWithoutLogo = createTestee(showLogo = false)
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testeeWithoutLogo.onStart(mockLifecycleOwner)

        testeeWithoutLogo.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.shouldShowLogo)
                assertFalse(it.hasContent)
            }
        }
    }

    @Test
    fun `when AppTP enabled, then show logo`() = runTest {
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.hasContent)
                assertTrue(it.shouldShowLogo)
            }
        }
    }

    @Test
    fun `when AppTP enabled and logo disabled, then hide logo`() = runTest {
        val testeeWithoutLogo = createTestee(showLogo = false)
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testeeWithoutLogo.onStart(mockLifecycleOwner)

        testeeWithoutLogo.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.shouldShowLogo)
                assertTrue(it.hasContent)
            }
        }
    }

    @Test
    fun `when onboarding complete and RMF available, then hide logo`() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.shouldShowLogo)
                assertTrue(it.hasContent)
            }
        }
    }

    @Test
    fun `when favorites available, then hide logo`() = runTest {
        val favorites = listOf(
            SavedSite.Favorite("1", "Test", "https://test.com", lastModified = "2024-01-01", 0),
        )
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(favorites))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(false)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.shouldShowLogo)
                assertTrue(it.hasContent)
            }
        }
    }

    @Test
    fun `when favourites loading, then hide logo but still report content`() = runTest {
        val favouritesFlow = MutableSharedFlow<List<SavedSite.Favorite>>(replay = 0)
        val remoteMessageFlow = MutableSharedFlow<RemoteMessage?>(replay = 0)
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(favouritesFlow)
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(remoteMessageFlow)

        testee = createTestee()
        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.shouldShowLogo)
                assertTrue(it.hasContent)
                assertNull(it.favourites)
            }
        }
    }

    @Test
    fun `when low priority message available, then show logo`() = runTest {
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
        whenever(mockLowPriorityMessagingModel.getMessage()).thenReturn(lowPriorityMessage)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.shouldShowLogo)
                assertTrue(it.hasContent)
            }
        }
    }

    @Test
    fun `when low priority message available and logo disabled, then hide logo`() = runTest {
        val testeeWithoutLogo = createTestee(showLogo = false)
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
        whenever(mockLowPriorityMessagingModel.getMessage()).thenReturn(lowPriorityMessage)

        testeeWithoutLogo.onStart(mockLifecycleOwner)

        testeeWithoutLogo.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.shouldShowLogo)
                assertTrue(it.hasContent)
            }
        }
    }

    @Test
    fun `when remote message available with MODAL surface then show logo, not the message`() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.MODAL))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.shouldShowLogo)
                assertNull(it.message)
            }
        }
    }

    @Test
    fun `when remote message available with NEW_TAB_PAGE surface then show the message, not the logo`() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.shouldShowLogo)
                assertEquals(remoteMessage, it.message)
            }
        }
    }

    @Test
    fun `when no remote message available then show the logo`() = runTest {
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(null))
        whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.shouldShowLogo)
                assertNull(it.message)
            }
        }
    }

    @Test
    fun whenRemoteImageLoadSuccessThenPixelFired() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        testee.onStart(mockLifecycleOwner)
        testee.onRemoteImageLoadSuccess()

        verify(pixel).fire(
            RemoteMessagingPixelName.REMOTE_MESSAGE_IMAGE_LOAD_SUCCESS,
            mapOf(Pixel.PixelParameter.MESSAGE_SHOWN to "id1"),
        )
    }

    @Test
    fun whenRemoteImageLoadFailedThenPixelFired() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))

        testee.onStart(mockLifecycleOwner)
        testee.onRemoteImageLoadFailed()

        verify(pixel).fire(
            RemoteMessagingPixelName.REMOTE_MESSAGE_IMAGE_LOAD_FAILED,
            mapOf(Pixel.PixelParameter.MESSAGE_SHOWN to "id1"),
        )
    }
}
