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

package com.duckduckgo.remote.messaging.newtab

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Action.DefaultBrowser
import com.duckduckgo.remote.messaging.api.Action.Survey
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.Command.SubmitUrl
import com.duckduckgo.remote.messaging.impl.pixels.RemoteMessagingPixelName
import com.duckduckgo.survey.api.SurveyParameterManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RemoteMessageViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RemoteMessageViewModel
    private var mockLifecycleOwner: LifecycleOwner = mock()
    private var remoteMessageModel: RemoteMessageModel = mock()
    private var playStoreUtils: PlayStoreUtils = mock()
    private var surveyParameterManager: SurveyParameterManager = mock()
    private var pixel: Pixel = mock()

    @Before
    fun setUp() {
        testee = RemoteMessageViewModel(
            coroutineRule.testDispatcherProvider,
            remoteMessageModel,
            playStoreUtils,
            surveyParameterManager,
            pixel,
        )
    }

    @Test
    fun whenViewModelInitialisedWithoutMessageThenViewStateEmitInitState() = runTest {
        whenever(remoteMessageModel.getActiveMessages()).thenReturn(flowOf(null))
        testee.onStart(mockLifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.newMessage)
            }
        }
    }

    @Test
    fun whenViewModelInitialisedWithMessageAndModalSurfaceThenViewStateEmitInitStateWithNoMessageToShow() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.MODAL))
        whenever(remoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        testee.onStart(mockLifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.newMessage)
            }
        }
    }

    @Test
    fun whenViewModelInitialisedThenViewStateEmitInitState() = runTest {
        val remoteMessage = whenRemoteMessageAvailable()

        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.newMessage)
                assertTrue(it.message?.id == remoteMessage.id)
                assertTrue(it.message?.content == remoteMessage.content)
                assertTrue(it.messageImageFilePath == null)
            }
        }
    }

    @Test
    fun whenViewModelInitialisedAndMessageWithSavedImageThenViewStateEmitInitState() = runTest {
        val remoteMessage = whenRemoteMessageAvailable("imagePath")

        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.newMessage)
                assertTrue(it.message?.id == remoteMessage.id)
                assertTrue(it.message?.content == remoteMessage.content)
                assertTrue(it.messageImageFilePath == "imagePath")
            }
        }
    }

    @Test
    fun whenMessageShownThenRemoteMessagingModelUpdated() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(remoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        testee.onStart(mockLifecycleOwner)

        testee.onMessageShown()
        verify(remoteMessageModel).onMessageShown(remoteMessage)
    }

    @Test
    fun whenMessageCloseButtonCLickedThenRemoteMessagingModelDismissed() = runTest {
        val remoteMessage = whenRemoteMessageAvailable()

        testee.onMessageCloseButtonClicked()
        verify(remoteMessageModel).onMessageDismissed(remoteMessage)
        verify(remoteMessageModel).clearMessageImage(Surface.NEW_TAB_PAGE)
    }

    @Test
    fun whenMessagePrimaryButtonCLickedThenRemoteMessagingModelDismissed() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(remoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(remoteMessageModel.onPrimaryActionClicked(remoteMessage)).thenReturn(DefaultBrowser)
        testee.onStart(mockLifecycleOwner)

        testee.commands().test {
            testee.onMessagePrimaryButtonClicked()
            verify(remoteMessageModel).onPrimaryActionClicked(remoteMessage)
            verify(remoteMessageModel).clearMessageImage(Surface.NEW_TAB_PAGE)
            val command = awaitItem()
            assertTrue(command is RemoteMessageViewModel.Command.LaunchDefaultBrowser)
        }
    }

    @Test
    fun whenMessageSecondaryButtonCLickedThenRemoteMessagingModelDismissed() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(remoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(remoteMessageModel.onSecondaryActionClicked(remoteMessage)).thenReturn(DefaultBrowser)
        testee.onStart(mockLifecycleOwner)

        testee.commands().test {
            testee.onMessageSecondaryButtonClicked()
            verify(remoteMessageModel).onSecondaryActionClicked(remoteMessage)
            verify(remoteMessageModel).clearMessageImage(Surface.NEW_TAB_PAGE)
            val command = awaitItem()
            assertTrue(command is RemoteMessageViewModel.Command.LaunchDefaultBrowser)
        }
    }

    @Test
    fun whenMessageActionCLickedThenRemoteMessagingModelDismissed() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(remoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(remoteMessageModel.onActionClicked(remoteMessage)).thenReturn(DefaultBrowser)
        testee.onStart(mockLifecycleOwner)

        testee.commands().test {
            testee.onMessageActionButtonClicked()
            verify(remoteMessageModel).onActionClicked(remoteMessage)
            verify(remoteMessageModel).clearMessageImage(Surface.NEW_TAB_PAGE)
            val command = awaitItem()
            assertTrue(command is RemoteMessageViewModel.Command.LaunchDefaultBrowser)
        }
    }

    @Test
    fun whenMessageActionClickedIsShareThenImageNotCleared() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        val shareAction = Action.Share("https://example.com", mapOf("title" to "Share Title"))
        whenever(remoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(remoteMessageModel.onActionClicked(remoteMessage)).thenReturn(shareAction)
        testee.onStart(mockLifecycleOwner)

        testee.commands().test {
            testee.onMessageActionButtonClicked()
            verify(remoteMessageModel).onActionClicked(remoteMessage)
            verify(remoteMessageModel, never()).clearMessageImage(any())
            val command = awaitItem()
            assertTrue(command is RemoteMessageViewModel.Command.SharePromoLinkRMF)
        }
    }

    @Test
    fun whenMessageActionClickedIsSurveyThenSubmitUrlCommandSent() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(surveyParameterManager.buildSurveyUrl("https://example.com", listOf("atb"))).thenReturn("https://example.com?atb")
        whenever(remoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(remoteMessageModel.onPrimaryActionClicked(remoteMessage)).thenReturn(
            Survey("https://example.com", mapOf("queryParams" to "atb")),
        )
        testee.onStart(mockLifecycleOwner)

        testee.commands().test {
            testee.onMessagePrimaryButtonClicked()
            verify(remoteMessageModel).onPrimaryActionClicked(remoteMessage)
            verify(remoteMessageModel).clearMessageImage(Surface.NEW_TAB_PAGE)
            val command = awaitItem()
            assertTrue(command is SubmitUrl)
            assertEquals("https://example.com?atb", (command as SubmitUrl).url)
        }
    }

    @Test
    fun whenPlayStoreOpenThenPlayStoreUtilsLaunched() {
        val appPackage = "package"
        testee.openPlayStore(appPackage)
        verify(playStoreUtils).launchPlayStore(appPackage)
    }

    @Test
    fun whenRemoteImageLoadSuccessThenPixelFired() = runTest {
        whenRemoteMessageAvailable()

        testee.onRemoteImageLoadSuccess()

        verify(pixel).fire(
            RemoteMessagingPixelName.REMOTE_MESSAGE_IMAGE_LOAD_SUCCESS,
            mapOf(Pixel.PixelParameter.MESSAGE_SHOWN to "id1"),
        )
    }

    @Test
    fun whenRemoteImageLoadFailedThenPixelFired() = runTest {
        whenRemoteMessageAvailable()

        testee.onRemoteImageLoadFailed()

        verify(pixel).fire(
            RemoteMessagingPixelName.REMOTE_MESSAGE_IMAGE_LOAD_FAILED,
            mapOf(Pixel.PixelParameter.MESSAGE_SHOWN to "id1"),
        )
    }

    private suspend fun whenRemoteMessageAvailable(imageFilePath: String? = null): RemoteMessage {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList(), listOf(Surface.NEW_TAB_PAGE))
        whenever(remoteMessageModel.getActiveMessages()).thenReturn(flowOf(remoteMessage))
        whenever(remoteMessageModel.getRemoteMessageImageFile(Surface.NEW_TAB_PAGE)).thenReturn(imageFilePath)
        testee.onStart(mockLifecycleOwner)
        return remoteMessage
    }
}
