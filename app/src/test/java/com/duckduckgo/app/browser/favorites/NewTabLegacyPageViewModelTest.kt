package com.duckduckgo.app.browser.favorites

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.app.browser.remotemessage.RemoteMessagingModel
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.engine.SyncEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NewTabLegacyPageViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private var mockLifecycleOwner: LifecycleOwner = mock()
    private var mockRemoteMessagingRepository: RemoteMessagingRepository = mock()
    private var mockPlayStoreUtils: PlayStoreUtils = mock()
    private var mockSavedSitesRepository: SavedSitesRepository = mock()
    private var mockSyncEngine: SyncEngine = mock()
    private var mockPixel: Pixel = mock()

    private lateinit var remoteMessagingModel: RemoteMessagingModel
    private val remoteMessageFlow = Channel<RemoteMessage>()

    private lateinit var testee: NewTabLegacyPageViewModel

    @Before
    fun setUp() {
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
        whenever(mockRemoteMessagingRepository.messageFlow()).thenReturn(remoteMessageFlow.consumeAsFlow())

        remoteMessagingModel = givenRemoteMessagingModel(mockRemoteMessagingRepository, mockPixel, coroutinesTestRule.testDispatcherProvider)

        testee = NewTabLegacyPageViewModel(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            remoteMessagingModel = { remoteMessagingModel },
            playStoreUtils = mockPlayStoreUtils,
            savedSitesRepository = mockSavedSitesRepository,
            syncEngine = mockSyncEngine,
        )
    }

    @After
    fun after() {
        remoteMessageFlow.close()
    }

    @Test
    fun whenViewModelIsInitializedThenViewStateShouldEmitInitialState() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)

        testee.onStart(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertEquals(it.message, remoteMessage)
                assertTrue(it.favourites.isEmpty())
                assertFalse(it.newMessage)
            }
        }
    }

    @Test
    fun whenRemoteMessageShownThenFirePixelAndMarkAsShown() = runTest {
        testee.onStart(mockLifecycleOwner)

        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)

        testee.onMessageShown()

        verify(mockRemoteMessagingRepository).markAsShown(remoteMessage)
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_SHOWN_UNIQUE, mapOf("message" to "id1"))
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_SHOWN, mapOf("message" to "id1"))
    }

    @Test
    fun whenRemoteMessageCloseButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)

        testee.onMessageCloseButtonClicked()

        verify(mockRemoteMessagingRepository).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_DISMISSED, mapOf("message" to "id1"))
    }

    @Test
    fun whenRemoteMessagePrimaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)

        testee.onMessagePrimaryButtonClicked()

        verify(mockRemoteMessagingRepository).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_PRIMARY_ACTION_CLICKED, mapOf("message" to "id1"))
    }

    @Test
    fun whenRemoteMessageSecondaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)

        testee.onMessageSecondaryButtonClicked()

        verify(mockRemoteMessagingRepository).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_SECONDARY_ACTION_CLICKED, mapOf("message" to "id1"))
    }

    @Test
    fun whenRemoteMessageActionButtonClickedThenFirePixelAndDontDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)

        testee.onMessageActionButtonClicked()

        verify(mockRemoteMessagingRepository, never()).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_ACTION_CLICKED, mapOf("message" to "id1"))
    }

    private fun givenRemoteMessagingModel(
        remoteMessagingRepository: RemoteMessagingRepository,
        pixel: Pixel,
        dispatchers: DispatcherProvider,
    ) = RemoteMessagingModel(remoteMessagingRepository, pixel, dispatchers)

    private suspend fun givenRemoteMessage(remoteMessage: RemoteMessage) {
        remoteMessageFlow.send(remoteMessage)
    }
}
