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
import com.duckduckgo.browsermode.api.BrowserMode
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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class RealDuckAiTabGridItemRendererTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val regularStore: DuckAiChatStore = mock()
    private val fireStore: DuckAiChatStore = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val voiceSessionStateManager: VoiceSessionStateManager = mock()
    private val duckChat: DuckChat = mock()

    private val testee = RealDuckAiTabGridItemRenderer(
        regularChatStore = regularStore,
        fireChatStore = fireStore,
        duckChatFeature = duckChatFeature,
        voiceSessionStateManager = voiceSessionStateManager,
        duckChat = duckChat,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setup() = runTest {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        duckChatFeature.useNativeStorageChatData().setRawStoredState(State(enable = true))
        whenever(regularStore.hasMigrated()).thenReturn(true)
        whenever(fireStore.hasMigrated()).thenReturn(true)
    }

    @Test
    fun whenVoiceSessionActiveThenActiveVoiceContentEvenWithoutStorage() = runTest {
        whenever(voiceSessionStateManager.isVoiceSessionActive(TAB_ID)).thenReturn(true)
        duckChatFeature.useNativeStorageChatData().setRawStoredState(State(enable = false))

        assertEquals(RealDuckAiTabGridItemContent.ActiveVoice, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenUrlHasNoChatIdThenEmptyContent() = runTest {
        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, "https://duck.ai/", BrowserMode.REGULAR)))
        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, null, BrowserMode.REGULAR)))
    }

    @Test
    fun whenNativeStorageDisabledThenFallback() = runTest {
        duckChatFeature.useNativeStorageChatData().setRawStoredState(State(enable = false))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenNotMigratedThenFallback() = runTest {
        whenever(regularStore.hasMigrated()).thenReturn(false)

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenResolvingSeveralTabsThenMigrationCheckedOnce() = runTest {
        whenever(regularStore.getChatPreview(any())).thenReturn(preview(lastMessageText = "Answer"))

        testee.load(DuckAiTabGridItemKey("tab-1", CHAT_URL, BrowserMode.REGULAR))
        testee.load(DuckAiTabGridItemKey("tab-2", "https://duck.ai/?chatID=chat-2", BrowserMode.REGULAR))

        verify(regularStore, times(1)).hasMigrated()
    }

    @Test
    fun whenNotMigratedThenNotCheckedAgainForTheOpening() = runTest {
        whenever(regularStore.hasMigrated()).thenReturn(false, true)

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
        verify(regularStore, times(1)).hasMigrated()
    }

    @Test
    fun whenStorageCheckedForOneModeThenOtherModeCheckedSeparately() = runTest {
        whenever(regularStore.hasMigrated()).thenReturn(false)
        whenever(fireStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Fire"))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Fire"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.FIRE)))
    }

    @Test
    fun whenChatNotFoundThenFallback() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(null)

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenTextChatWithLastMessageThenChatContent() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Answer"))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenLastMessageIsLongThenSnippetCappedAt500Characters() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "a".repeat(600)))

        assertEquals(
            RealDuckAiTabGridItemContent.Chat("Title", "a".repeat(500)),
            testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)),
        )
    }

    @Test
    fun whenVoiceChatWithLastMessageThenTranscriptContent() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(isVoice = true, lastMessageText = "Said"))

        assertEquals(
            RealDuckAiTabGridItemContent.VoiceTranscript("Title", "Said"),
            testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)),
        )
    }

    @Test
    fun whenVoiceChatWithoutTextThenEmptyContent() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(isVoice = true, lastMessageText = null, isAwaitingReply = true))

        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenAwaitingReplyThenThinkingContent() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Question", isAwaitingReply = true))

        assertEquals(RealDuckAiTabGridItemContent.Thinking("Title"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenTextChatWithoutContentThenEmptyContent() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = null))

        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenImageChatThenImageContentWithLastFileRef() = runTest {
        // The store reports a finished image chat as awaiting a reply, since its last assistant message has no text.
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(
            preview(isImageGeneration = true, fileRefs = listOf("first", "last"), isAwaitingReply = true),
        )

        assertEquals(
            RealDuckAiTabGridItemContent.ImageChat("Title", "last", thumbnail = null),
            testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)),
        )
    }

    @Test
    fun whenImageChatWithoutFileRefsThenEmptyContent() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(isImageGeneration = true))

        assertEquals(RealDuckAiTabGridItemContent.Empty, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenFireModeThenReadsFromFireStore() = runTest {
        whenever(fireStore.getChatPreview(CHAT_ID)).thenReturn(preview(isImageGeneration = true, fileRefs = listOf("ref")))

        assertEquals(
            RealDuckAiTabGridItemContent.ImageChat("Title", "ref", thumbnail = null),
            testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.FIRE)),
        )
        verify(regularStore, never()).getChatPreview(any())
    }

    @Test
    fun whenChatReadFailsThenFallback() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenChatReadFailsThenNotReadAgainOnNextResolve() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))

        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
        verify(regularStore, times(1)).getChatPreview(CHAT_ID)
    }

    @Test
    fun whenChatReadFailsInOneModeThenOtherModeStillRead() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))
        whenever(fireStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Fire"))

        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Fire"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.FIRE)))
    }

    @Test
    fun whenChatReadFailsThenVoiceSessionStillShown() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
        whenever(voiceSessionStateManager.isVoiceSessionActive(TAB_ID)).thenReturn(true)

        assertEquals(RealDuckAiTabGridItemContent.ActiveVoice, testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenNothingLoadedYetThenPeekNeedsALoad() {
        assertNull(testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenChatLoadedThenPeekReturnsItWithoutReading() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Answer"))
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
        verify(regularStore, times(1)).getChatPreview(CHAT_ID)
    }

    @Test
    fun whenChatIsThinkingThenPeekNeedsALoadAgain() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Question", isAwaitingReply = true))
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))

        assertNull(testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenStorageKnownNotReadyThenPeekReturnsFallback() = runTest {
        whenever(regularStore.hasMigrated()).thenReturn(false)
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.peek(DuckAiTabGridItemKey("other", CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenChatUnreadableThenPeekReturnsFallback() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))

        assertEquals(RealDuckAiTabGridItemContent.Fallback, testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenVoiceSessionActiveThenPeekReturnsActiveVoiceWithoutLoading() {
        whenever(voiceSessionStateManager.isVoiceSessionActive(TAB_ID)).thenReturn(true)

        assertEquals(RealDuckAiTabGridItemContent.ActiveVoice, testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenChatResolvedAgainThenPreviewReadOnce() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Answer"), preview(lastMessageText = "Newer"))

        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
        verify(regularStore, times(1)).getChatPreview(CHAT_ID)
    }

    @Test
    fun whenChatNotFoundThenReadAgainOnNextResolve() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(null, preview(lastMessageText = "Answer"))

        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    fun whenSameChatIdInOtherModeThenPreviewNotShared() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Regular"))
        whenever(fireStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Fire"))

        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Fire"), testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.FIRE)))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenImageChatLoadedAgainThenCachedThumbnailReturnedWithoutRereading() = runTest {
        givenImageChat(regularStore)
        whenever(regularStore.readFileRef(FILE_REF)).thenReturn(pngFileRef())

        val first = loadImageChat(BrowserMode.REGULAR)
        val second = loadImageChat(BrowserMode.REGULAR)

        assertNotNull(first.thumbnail)
        assertSame(first.thumbnail, second.thumbnail)
        verify(regularStore, times(1)).getChatPreview(CHAT_ID)
        verify(regularStore, times(1)).readFileRef(FILE_REF)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenImageChatLoadedThenPeekReturnsItWithThumbnail() = runTest {
        givenImageChat(regularStore)
        whenever(regularStore.readFileRef(FILE_REF)).thenReturn(pngFileRef())
        val loaded = loadImageChat(BrowserMode.REGULAR)

        assertEquals(loaded, testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenSameFileRefInOtherModeThenThumbnailNotShared() = runTest {
        givenImageChat(regularStore)
        givenImageChat(fireStore)
        whenever(regularStore.readFileRef(FILE_REF)).thenReturn(pngFileRef())
        whenever(fireStore.readFileRef(FILE_REF)).thenReturn(pngFileRef())

        loadImageChat(BrowserMode.REGULAR)
        loadImageChat(BrowserMode.FIRE)

        verify(fireStore).readFileRef(FILE_REF)
    }

    @Test
    fun whenThumbnailMissingThenNullAndOnlyThumbnailReadAgainOnNextLoad() = runTest {
        givenImageChat(regularStore)
        whenever(regularStore.readFileRef(FILE_REF)).thenReturn(null)

        assertNull(loadImageChat(BrowserMode.REGULAR).thumbnail)
        assertNull(testee.peek(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, BrowserMode.REGULAR)))
        loadImageChat(BrowserMode.REGULAR)

        verify(regularStore, times(1)).getChatPreview(CHAT_ID)
        verify(regularStore, times(2)).readFileRef(FILE_REF)
    }

    @Test
    fun whenThumbnailReadThrowsThenImageChatWithoutThumbnail() = runTest {
        givenImageChat(regularStore)
        whenever(regularStore.readFileRef(FILE_REF)).thenThrow(IllegalStateException("read failed"))

        assertNull(loadImageChat(BrowserMode.REGULAR).thumbnail)
    }

    @Test
    fun whenThumbnailDecodeRunsOutOfMemoryThenImageChatWithoutThumbnail() = runTest {
        givenImageChat(regularStore)
        whenever(regularStore.readFileRef(FILE_REF)).thenAnswer { throw OutOfMemoryError() }

        assertNull(loadImageChat(BrowserMode.REGULAR).thumbnail)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenImageIsLargeThenShortSideSampledDownToTarget() = runTest {
        givenImageChat(regularStore)
        whenever(regularStore.readFileRef(FILE_REF)).thenReturn(pngFileRef(width = 2048, height = 2048))

        val bitmap = loadImageChat(BrowserMode.REGULAR).thumbnail!!

        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun whenImageIsVeryTallThenSampledUnderPixelCap() = runTest {
        givenImageChat(regularStore)
        whenever(regularStore.readFileRef(FILE_REF)).thenReturn(pngFileRef(width = 512, height = 8192))

        val bitmap = loadImageChat(BrowserMode.REGULAR).thumbnail!!

        assertTrue(bitmap.width.toLong() * bitmap.height <= 1024 * 1024)
    }

    private suspend fun givenImageChat(store: DuckAiChatStore) {
        whenever(store.getChatPreview(CHAT_ID)).thenReturn(preview(isImageGeneration = true, fileRefs = listOf(FILE_REF)))
    }

    private suspend fun loadImageChat(mode: BrowserMode): RealDuckAiTabGridItemContent.ImageChat =
        testee.load(DuckAiTabGridItemKey(TAB_ID, CHAT_URL, mode)) as RealDuckAiTabGridItemContent.ImageChat

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
