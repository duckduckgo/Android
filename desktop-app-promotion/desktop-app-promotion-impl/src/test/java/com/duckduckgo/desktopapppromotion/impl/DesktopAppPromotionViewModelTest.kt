/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.desktopapppromotion.impl

import app.cash.turbine.test
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import com.duckduckgo.desktopapppromotion.impl.DesktopAppPromotionViewModel.Command
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopAppPromotionViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val pixelMock: Pixel = mock()
    private val clipboardInteractorMock: ClipboardInteractor = mock()
    private val interactionDispatcher = FakeInteractionDispatcher()

    private fun createViewModel(
        handlerId: String? = null,
    ) = DesktopAppPromotionViewModel(
        content = content,
        handlerId = handlerId,
        pixel = pixelMock,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        clipboardInteractor = clipboardInteractorMock,
        interactionDispatcher = interactionDispatcher,
    )

    @Test
    fun whenCreatedThenViewStateCarriesResolvedContent() = runTest {
        createViewModel().viewState.test {
            assertEquals(content, awaitItem().content)
        }
    }

    @Test
    fun whenShareClickedThenShareLinkCommandCarriesShareIntentBody() = runTest {
        val testee = createViewModel()

        testee.commands.test {
            testee.onShareClicked()

            val command = awaitItem() as Command.ShareLink
            assertEquals(SHARE_BODY, command.shareText)
            assertEquals(SHARE_TITLE, command.chooserTitle)
        }
    }

    @Test
    fun whenShareClickedAndNoShareIntentBodyThenShareLinkCommandCarriesBareUrl() = runTest {
        val testee = DesktopAppPromotionViewModel(
            content = content.copy(shareIntentBody = null),
            handlerId = null,
            pixel = pixelMock,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            clipboardInteractor = clipboardInteractorMock,
            interactionDispatcher = interactionDispatcher,
        )

        testee.commands.test {
            testee.onShareClicked()

            val command = awaitItem() as Command.ShareLink
            assertEquals(DOWNLOAD_URL, command.shareText)
        }
    }

    @Test
    fun whenLinkClickedAndSystemShowsNotificationThenDoNotEmitShowCopiedNotification() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel()

        testee.commands.test {
            testee.onLinkClicked()
            expectNoEvents()
        }
    }

    @Test
    fun whenLinkClickedAndSystemDoesNotShowNotificationThenEmitShowCopiedNotification() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(false)
        val testee = createViewModel()

        testee.commands.test {
            testee.onLinkClicked()

            assertEquals(Command.ShowCopiedNotification, awaitItem())
        }
    }

    @Test
    fun whenLinkClickedThenCopiesTheAttributedUrl() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)

        createViewModel().onLinkClicked()

        verify(clipboardInteractorMock).copyToClipboard(eq(DOWNLOAD_URL), eq(false))
    }

    @Test
    fun whenDismissClickedThenEmitClose() = runTest {
        val testee = createViewModel()

        testee.commands.test {
            testee.onDismissClicked()

            assertEquals(Command.Close, awaitItem())
        }
    }

    @Test
    fun whenCreatedThenImpressionInteractionIsDispatchedOnce() = runTest {
        createViewModel(handlerId = HANDLER_ID)

        assertEquals(listOf(HANDLER_ID to Interaction.IMPRESSION), interactionDispatcher.dispatched)
    }

    @Test
    fun whenBackPressedThenEmitCloseWithoutDispatchingAnyFurtherInteraction() = runTest {
        val testee = createViewModel(handlerId = HANDLER_ID)

        testee.commands.test {
            testee.onBackPressed()

            assertEquals(Command.Close, awaitItem())
        }

        assertEquals(listOf(HANDLER_ID to Interaction.IMPRESSION), interactionDispatcher.dispatched)
    }

    @Test
    fun whenShareClickedThenShareClickedInteractionIsDispatched() = runTest {
        val testee = createViewModel(handlerId = HANDLER_ID)

        testee.onShareClicked()

        assertEquals(
            listOf(HANDLER_ID to Interaction.IMPRESSION, HANDLER_ID to Interaction.SHARE_CLICKED),
            interactionDispatcher.dispatched,
        )
    }

    @Test
    fun whenShareClickedAndNoHandlerHandlesItThenDefaultSharePixelFiresWithoutParams() = runTest {
        interactionDispatcher.result = false
        val testee = createViewModel(handlerId = HANDLER_ID)

        testee.onShareClicked()

        verify(pixelMock).fire(eq("m_get_desktop_browser_share_download_link_click"), eq(hashMapOf()), any(), any())
    }

    @Test
    fun whenShareClickedAndAHandlerHandlesItThenNoDefaultPixelFires() = runTest {
        interactionDispatcher.result = true
        val testee = createViewModel(handlerId = HANDLER_ID)

        testee.onShareClicked()

        verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
    }

    @Test
    fun whenLinkClickedThenLinkCopiedInteractionIsDispatched() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel(handlerId = HANDLER_ID)

        testee.onLinkClicked()

        assertEquals(
            listOf(HANDLER_ID to Interaction.IMPRESSION, HANDLER_ID to Interaction.LINK_COPIED),
            interactionDispatcher.dispatched,
        )
    }

    @Test
    fun whenLinkClickedAndNoHandlerHandlesItThenDefaultLinkPixelFiresWithoutParams() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        interactionDispatcher.result = false
        val testee = createViewModel(handlerId = HANDLER_ID)

        testee.onLinkClicked()

        verify(pixelMock).fire(eq("m_get_desktop_browser_link_click"), eq(hashMapOf()), any(), any())
    }

    @Test
    fun whenLinkClickedAndAHandlerHandlesItThenNoDefaultPixelFires() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        interactionDispatcher.result = true
        val testee = createViewModel(handlerId = HANDLER_ID)

        testee.onLinkClicked()

        verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
    }

    @Test
    fun whenDismissClickedThenDismissedInteractionIsDispatchedAndNoDefaultPixelFires() = runTest {
        val testee = createViewModel(handlerId = HANDLER_ID)

        testee.onDismissClicked()

        assertEquals(
            listOf(HANDLER_ID to Interaction.IMPRESSION, HANDLER_ID to Interaction.DISMISSED),
            interactionDispatcher.dispatched,
        )
        verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
    }

    @Test
    fun whenLaunchCarriesNoHandlerIdThenNullIsDispatchedAndDefaultPixelsStillFire() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel(handlerId = null)

        testee.onShareClicked()
        testee.onLinkClicked()
        testee.onDismissClicked()

        assertEquals(
            listOf(
                null to Interaction.IMPRESSION,
                null to Interaction.SHARE_CLICKED,
                null to Interaction.LINK_COPIED,
                null to Interaction.DISMISSED,
            ),
            interactionDispatcher.dispatched,
        )
        verify(pixelMock).fire(eq("m_get_desktop_browser_share_download_link_click"), eq(hashMapOf()), any(), any())
        verify(pixelMock).fire(eq("m_get_desktop_browser_link_click"), eq(hashMapOf()), any(), any())
    }

    private class FakeInteractionDispatcher : DesktopAppPromotionInteractionDispatcher {
        val dispatched = mutableListOf<Pair<String?, Interaction>>()
        var result = false

        override suspend fun dispatch(
            handlerId: String?,
            interaction: Interaction,
        ): Boolean {
            dispatched += handlerId to interaction
            return result
        }
    }

    companion object {
        private const val HANDLER_ID = "test_handler"
        private const val DOWNLOAD_URL = "https://duckduckgo.com/browser?origin=funnel_test"
        private const val SHARE_BODY = "Get DuckDuckGo: https://duckduckgo.com/browser?origin=funnel_test"
        private const val SHARE_TITLE = "Share Download Link"

        private val content = DesktopAppPromotionContent(
            toolbarTitle = "Get Desktop Browser",
            title = "Protect your personal info on Mac and Windows too!",
            body = "To download DuckDuckGo on Mac or Windows, visit:",
            illustration = 1,
            downloadUrlDisplay = "duckduckgo.com/browser",
            downloadUrl = DOWNLOAD_URL,
            shareButtonLabel = SHARE_TITLE,
            shareIntentTitle = SHARE_TITLE,
            shareIntentBody = SHARE_BODY,
            showDismissButton = true,
            dismissButtonLabel = "No Thanks",
            linkCopiedMessage = "Link copied",
        )
    }
}
