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

package com.duckduckgo.duckchat.impl.tabswitcher

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemKey
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatPreview
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.duckchat.store.impl.FileRefContent
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class RealDuckAiTabGridItemRendererTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val chatStore: DuckAiChatStore = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val voiceSessionStateManager: VoiceSessionStateManager = mock()
    private val duckChat: DuckChat = mock()

    private val testee = RealDuckAiTabGridItemRenderer(
        chatStore = chatStore,
        duckChatFeature = duckChatFeature,
        voiceSessionStateManager = voiceSessionStateManager,
        duckChat = duckChat,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setup() = runTest {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        duckChatFeature.useNativeStorageChatData().setRawStoredState(State(enable = true))
        whenever(chatStore.hasMigrated()).thenReturn(true)
    }

    @Test
    fun whenVoiceSessionActiveThenActiveVoiceContentEvenWithoutStorage() = runTest {
        whenever(voiceSessionStateManager.isVoiceSessionActive(TAB_ID)).thenReturn(true)
        duckChatFeature.useNativeStorageChatData().setRawStoredState(State(enable = false))

        assertEquals(RealDuckAiTabGridItemContent.ActiveVoice, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenUrlHasNoChatIdThenEmptyContent() = runTest {
        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, "https://duck.ai/")))
        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, null)))
    }

    @Test
    fun whenNativeStorageDisabledThenFallback() = runTest {
        duckChatFeature.useNativeStorageChatData().setRawStoredState(State(enable = false))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenNotMigratedThenFallback() = runTest {
        whenever(chatStore.hasMigrated()).thenReturn(false)

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenResolvingSeveralTabsThenMigrationCheckedOnce() = runTest {
        whenever(chatStore.getChatPreview(any())).thenReturn(preview(lastMessageText = "Answer"))

        testee.load(DuckAiTabGridItemKey("tab-1", CHAT_URL))
        testee.load(DuckAiTabGridItemKey("tab-2", "https://duck.ai/?chatID=chat-2"))

        verify(chatStore, times(1)).hasMigrated()
    }

    @Test
    fun whenNotMigratedThenNotCheckedAgainForTheOpening() = runTest {
        whenever(chatStore.hasMigrated()).thenReturn(false, true)

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
        verify(chatStore, times(1)).hasMigrated()
    }

    @Test
    fun whenChatNotFoundThenFallback() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(null)

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenTextChatWithLastMessageThenChatContent() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Answer"))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenLastMessageIsLongThenSnippetCappedAt500Characters() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "a".repeat(600)))

        assertEquals(
            RealDuckAiTabGridItemContent.Chat("Title", "a".repeat(500)),
            testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)),
        )
    }

    @Test
    fun whenVoiceChatWithLastMessageThenTranscriptContent() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(isVoice = true, lastMessageText = "Said"))

        assertEquals(
            RealDuckAiTabGridItemContent.VoiceTranscript("Title", "Said"),
            testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)),
        )
    }

    @Test
    fun whenVoiceChatWithoutTextThenEmptyContent() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(isVoice = true, lastMessageText = null, isAwaitingReply = true))

        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenAwaitingReplyThenThinkingContent() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Question", isAwaitingReply = true))

        assertEquals(RealDuckAiTabGridItemContent.Thinking("Title"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenTextChatWithoutContentThenEmptyContent() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = null))

        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenImageChatThenImageContentWithLastFileRef() = runTest {
        // The store reports a finished image chat as awaiting a reply, since its last assistant message has no text.
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(
            preview(isImageGeneration = true, fileRefs = listOf("first", "last"), isAwaitingReply = true),
        )

        assertEquals(
            RealDuckAiTabGridItemContent.ImageChat("Title", "last", thumbnail = null),
            testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)),
        )
    }

    @Test
    fun whenImageChatWithoutFileRefsThenEmptyContent() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(isImageGeneration = true))

        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenChatReadFailsThenFallback() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenChatReadFailsThenNotReadAgainOnNextResolve() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))

        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
        verify(chatStore, times(1)).getChatPreview(CHAT_ID)
    }

    @Test
    fun whenChatReadFailsThenVoiceSessionStillShown() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL))
        whenever(voiceSessionStateManager.isVoiceSessionActive(TAB_ID)).thenReturn(true)

        assertEquals(RealDuckAiTabGridItemContent.ActiveVoice, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenNothingLoadedYetThenPeekNeedsALoad() {
        assertNull(testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenChatLoadedThenPeekReturnsItWithoutReading() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Answer"))
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
        verify(chatStore, times(1)).getChatPreview(CHAT_ID)
    }

    @Test
    fun whenChatIsThinkingThenPeekNeedsALoadAgain() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Question", isAwaitingReply = true))
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL))

        assertNull(testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenStorageKnownNotReadyThenPeekReturnsFallback() = runTest {
        whenever(chatStore.hasMigrated()).thenReturn(false)
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.peek(DuckAiTabGridItemKey("other", CHAT_URL)))
    }

    @Test
    fun whenChatUnreadableThenPeekReturnsFallback() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenVoiceSessionActiveThenPeekReturnsActiveVoiceWithoutLoading() {
        whenever(voiceSessionStateManager.isVoiceSessionActive(TAB_ID)).thenReturn(true)

        assertEquals(RealDuckAiTabGridItemContent.ActiveVoice, testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenChatResolvedAgainThenPreviewReadOnce() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Answer"), preview(lastMessageText = "Newer"))

        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
        verify(chatStore, times(1)).getChatPreview(CHAT_ID)
    }

    @Test
    fun whenChatNotFoundThenReadAgainOnNextResolve() = runTest {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(null, preview(lastMessageText = "Answer"))

        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenImageChatLoadedAgainThenCachedThumbnailReturnedWithoutRereading() = runTest {
        givenImageChat()
        whenever(chatStore.readFileRef(FILE_REF)).thenReturn(pngFileRef())

        val first = loadImageChat()
        val second = loadImageChat()

        assertNotNull(first.thumbnail)
        assertSame(first.thumbnail, second.thumbnail)
        verify(chatStore, times(1)).getChatPreview(CHAT_ID)
        verify(chatStore, times(1)).readFileRef(FILE_REF)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenImageChatLoadedThenPeekReturnsItWithThumbnail() = runTest {
        givenImageChat()
        whenever(chatStore.readFileRef(FILE_REF)).thenReturn(pngFileRef())
        val loaded = loadImageChat()

        assertEquals(loaded, testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
    }

    @Test
    fun whenThumbnailMissingThenNullAndOnlyThumbnailReadAgainOnNextLoad() = runTest {
        givenImageChat()
        whenever(chatStore.readFileRef(FILE_REF)).thenReturn(null)

        assertNull(loadImageChat().thumbnail)
        assertNull(testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)))
        loadImageChat()

        verify(chatStore, times(1)).getChatPreview(CHAT_ID)
        verify(chatStore, times(2)).readFileRef(FILE_REF)
    }

    @Test
    fun whenThumbnailReadThrowsThenImageChatWithoutThumbnail() = runTest {
        givenImageChat()
        whenever(chatStore.readFileRef(FILE_REF)).thenThrow(IllegalStateException("read failed"))

        assertNull(loadImageChat().thumbnail)
    }

    @Test
    fun whenThumbnailDecodeRunsOutOfMemoryThenImageChatWithoutThumbnail() = runTest {
        givenImageChat()
        whenever(chatStore.readFileRef(FILE_REF)).thenAnswer { throw OutOfMemoryError() }

        assertNull(loadImageChat().thumbnail)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenImageIsLargeThenShortSideSampledDownToTarget() = runTest {
        givenImageChat()
        whenever(chatStore.readFileRef(FILE_REF)).thenReturn(pngFileRef(width = 2048, height = 2048))

        val bitmap = loadImageChat().thumbnail!!

        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenImageIsVeryTallThenSampledUnderPixelCap() = runTest {
        givenImageChat()
        whenever(chatStore.readFileRef(FILE_REF)).thenReturn(pngFileRef(width = 512, height = 8192))

        val bitmap = loadImageChat().thumbnail!!

        assertTrue(bitmap.width.toLong() * bitmap.height <= 1024 * 1024)
    }

    private suspend fun givenImageChat() {
        whenever(chatStore.getChatPreview(CHAT_ID)).thenReturn(preview(isImageGeneration = true, fileRefs = listOf(FILE_REF)))
    }

    private suspend fun loadImageChat(): RealDuckAiTabGridItemContent.ImageChat =
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL)) as RealDuckAiTabGridItemContent.ImageChat

    private fun pngFileRef(width: Int = 4, height: Int = 4): FileRefContent {
        val output = ByteArrayOutputStream()
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
        return FileRefContent(fileName = null, mimeType = "image/png", bytes = output.toByteArray())
    }

    private fun preview(
        isImageGeneration: Boolean = false,
        isVoice: Boolean = false,
        fileRefs: List<String> = emptyList(),
        lastMessageText: String? = null,
        isAwaitingReply: Boolean = false,
    ) = DuckAiChatPreview(
        chat = DuckAiChat(
            chatId = CHAT_ID,
            title = "Title",
            model = "model",
            lastEdit = "2026-10-13T00:00:00.000Z",
            pinned = false,
            fileRefs = fileRefs,
            isImageGeneration = isImageGeneration,
            isVoice = isVoice,
        ),
        lastMessageText = lastMessageText,
        isAwaitingReply = isAwaitingReply,
    )

    private companion object {
        const val TAB_ID = "tab"
        const val CHAT_ID = "chat-1"
        const val CHAT_URL = "https://duck.ai/?chatID=$CHAT_ID"
        const val FILE_REF = "file-1"
    }
}
