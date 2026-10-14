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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatPreview
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        appCoroutineScope = coroutineTestRule.testScope,
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

        assertEquals(RealDuckAiTabGridItemContent.ActiveVoice, testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenUrlHasNoChatIdThenEmptyContentWithoutTitle() = runTest {
        assertEquals(RealDuckAiTabGridItemContent.Empty(title = null), testee.resolve(TAB_ID, "https://duck.ai/", BrowserMode.REGULAR))
        assertEquals(RealDuckAiTabGridItemContent.Empty(title = null), testee.resolve(TAB_ID, null, BrowserMode.REGULAR))
    }

    @Test
    fun whenNativeStorageDisabledThenNull() = runTest {
        duckChatFeature.useNativeStorageChatData().setRawStoredState(State(enable = false))

        assertNull(testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenNotMigratedThenNull() = runTest {
        whenever(regularStore.hasMigrated()).thenReturn(false)

        assertNull(testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenChatNotFoundThenNull() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(null)

        assertNull(testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenTextChatWithLastMessageThenChatContent() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Answer"))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenLastMessageIsLongThenSnippetCappedAt500Characters() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "a".repeat(600)))

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "a".repeat(500)), testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenTextChatWithoutContentThenEmptyContentKeepsTitle() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = null))

        assertEquals(RealDuckAiTabGridItemContent.Empty("Title"), testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenImageChatThenImageContentWithLastFileRef() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(isImageGeneration = true, fileRefs = listOf("first", "last")))

        assertEquals(
            RealDuckAiTabGridItemContent.ImageChat("Title", "last", BrowserMode.REGULAR),
            testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR),
        )
    }

    @Test
    fun whenImageChatWithoutFileRefsThenEmptyContentKeepsTitle() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(isImageGeneration = true))

        assertEquals(RealDuckAiTabGridItemContent.Empty("Title"), testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenFireModeThenReadsFromFireStore() = runTest {
        whenever(fireStore.getChatPreview(CHAT_ID)).thenReturn(preview(isImageGeneration = true, fileRefs = listOf("ref")))

        assertEquals(
            RealDuckAiTabGridItemContent.ImageChat("Title", "ref", BrowserMode.FIRE),
            testee.resolve(TAB_ID, CHAT_URL, BrowserMode.FIRE),
        )
        verify(regularStore, never()).getChatPreview(any())
    }

    @Test
    fun whenChatReadFailsThenNull() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenThrow(IllegalStateException("Row too big to fit into CursorWindow"))

        assertNull(testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenChatResolvedAgainThenPreviewReadOnce() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Answer"), preview(lastMessageText = "Newer"))

        testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR)

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
        verify(regularStore, times(1)).getChatPreview(CHAT_ID)
    }

    @Test
    fun whenChatNotFoundThenReadAgainOnNextResolve() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(null, preview(lastMessageText = "Answer"))

        testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR)

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Answer"), testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenSameChatIdInOtherModeThenPreviewNotShared() = runTest {
        whenever(regularStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Regular"))
        whenever(fireStore.getChatPreview(CHAT_ID)).thenReturn(preview(lastMessageText = "Fire"))

        testee.resolve(TAB_ID, CHAT_URL, BrowserMode.REGULAR)

        assertEquals(RealDuckAiTabGridItemContent.Chat("Title", "Fire"), testee.resolve(TAB_ID, CHAT_URL, BrowserMode.FIRE))
    }

    private fun preview(
        isImageGeneration: Boolean = false,
        fileRefs: List<String> = emptyList(),
        lastMessageText: String? = null,
    ) = DuckAiChatPreview(
        chat = DuckAiChat(
            chatId = CHAT_ID,
            title = "Title",
            model = "model",
            lastEdit = "2026-10-13T00:00:00.000Z",
            pinned = false,
            fileRefs = fileRefs,
            isImageGeneration = isImageGeneration,
        ),
        lastMessageText = lastMessageText,
    )

    private companion object {
        const val TAB_ID = "tab"
        const val CHAT_ID = "chat-1"
        const val CHAT_URL = "https://duck.ai/?chatID=$CHAT_ID"
    }
}
