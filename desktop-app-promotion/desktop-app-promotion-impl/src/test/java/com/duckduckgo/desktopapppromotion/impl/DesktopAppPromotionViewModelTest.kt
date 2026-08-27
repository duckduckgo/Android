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
import com.duckduckgo.desktopapppromotion.api.PixelConfig
import com.duckduckgo.desktopapppromotion.api.PixelFireSpec
import com.duckduckgo.desktopapppromotion.impl.DesktopAppPromotionViewModel.Command
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        pixels: PixelConfig = PixelConfig(),
        handlerId: String? = null,
    ) = DesktopAppPromotionViewModel(
        content = content,
        pixels = pixels,
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
            pixels = PixelConfig(),
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
    fun whenBackPressedThenEmitCloseWithoutPixelOrHandler() = runTest {
        val testee = createViewModel(pixels = allPixels(), handlerId = HANDLER_ID)

        testee.commands.test {
            testee.onBackPressed()

            assertEquals(Command.Close, awaitItem())
        }

        assertTrue(interactionDispatcher.dispatched.isEmpty())
        verify(pixelMock, never()).fire(eq(DISMISS_PIXEL), any(), any(), any())
    }

    @Test
    fun whenImpressionSpecPresentThenImpressionPixelFiresOnceOnCreation() = runTest {
        createViewModel(pixels = allPixels(), handlerId = HANDLER_ID)

        verify(pixelMock).fire(eq(IMPRESSION_PIXEL), eq(hashMapOf("source" to "test")), any(), any())
    }

    @Test
    fun whenSpecsPresentThenEachInteractionFiresItsConfiguredPixel() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel(pixels = allPixels(), handlerId = HANDLER_ID)

        testee.onShareClicked()
        testee.onLinkClicked()
        testee.onDismissClicked()

        verify(pixelMock).fire(eq(SHARE_PIXEL), eq(hashMapOf("source" to "test")), any(), any())
        verify(pixelMock).fire(eq(LINK_PIXEL), eq(hashMapOf("source" to "test")), any(), any())
        verify(pixelMock).fire(eq(DISMISS_PIXEL), eq(hashMapOf("source" to "test")), any(), any())
    }

    @Test
    fun whenSpecsAbsentThenNoPixelIsFiredForAnyInteraction() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel()

        testee.onShareClicked()
        testee.onLinkClicked()
        testee.onDismissClicked()

        verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
    }

    @Test
    fun whenInteractionsHappenThenTheyAreDispatchedWithTheLaunchHandlerId() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel(handlerId = HANDLER_ID)

        testee.onLinkClicked()
        testee.onDismissClicked()

        assertEquals(
            listOf(HANDLER_ID to Interaction.LINK_COPIED, HANDLER_ID to Interaction.DISMISSED),
            interactionDispatcher.dispatched,
        )
    }

    @Test
    fun whenLaunchCarriesNoHandlerIdThenNullIsDispatchedAndNoHandlerCanMatch() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel(handlerId = null)

        testee.onLinkClicked()
        testee.onDismissClicked()

        assertEquals(
            listOf(null to Interaction.LINK_COPIED, null to Interaction.DISMISSED),
            interactionDispatcher.dispatched,
        )
    }

    private fun allPixels() = PixelConfig(
        impression = PixelFireSpec(IMPRESSION_PIXEL, sourceParams()),
        shareClicked = PixelFireSpec(SHARE_PIXEL, sourceParams()),
        linkClicked = PixelFireSpec(LINK_PIXEL, sourceParams()),
        dismissed = PixelFireSpec(DISMISS_PIXEL, sourceParams()),
    )

    private fun sourceParams() = hashMapOf("source" to "test")

    private class FakeInteractionDispatcher : DesktopAppPromotionInteractionDispatcher {
        val dispatched = mutableListOf<Pair<String?, Interaction>>()

        override suspend fun dispatch(
            handlerId: String?,
            interaction: Interaction,
        ) {
            dispatched += handlerId to interaction
        }
    }

    companion object {
        private const val HANDLER_ID = "test_handler"
        private const val DOWNLOAD_URL = "https://duckduckgo.com/browser?origin=funnel_test"
        private const val SHARE_BODY = "Get DuckDuckGo: https://duckduckgo.com/browser?origin=funnel_test"
        private const val SHARE_TITLE = "Share Download Link"
        private const val IMPRESSION_PIXEL = "m_test_impression"
        private const val SHARE_PIXEL = "m_test_share"
        private const val LINK_PIXEL = "m_test_link"
        private const val DISMISS_PIXEL = "m_test_dismiss"

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
