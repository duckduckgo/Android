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

package com.duckduckgo.duckchat.impl.history

import android.content.Context
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChatHistoryRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val chatStore: DuckAiChatStore = mock()
    private val context: Context = mock()
    private val source = MutableStateFlow<List<DuckAiChat>>(emptyList())
    private lateinit var repository: RealChatHistoryRepository

    @Before
    fun setup() {
        whenever(context.getString(R.string.duck_ai_chat_history_untitled)).thenReturn(FALLBACK)
        whenever(chatStore.getChatsFlow()).thenReturn(source)
        repository = RealChatHistoryRepository(chatStore, coroutineRule.testDispatcherProvider, context)
    }

    @Test
    fun `observeChats emits empty list when source is empty`() = runTest {
        repository.observeChats().test {
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun `observeChats maps title and pinned through`() = runTest {
        source.value = listOf(chat(chatId = "abc", title = "Hello", pinned = true))

        repository.observeChats().test {
            val mapped = awaitItem().single()
            assertEquals("abc", mapped.chatId)
            assertEquals("Hello", mapped.displayTitle)
            assertEquals(true, mapped.pinned)
        }
    }

    @Test
    fun `observeChats falls back to localized untitled when title is blank`() = runTest {
        source.value = listOf(chat(chatId = "abc", title = "   "))

        repository.observeChats().test {
            assertEquals(FALLBACK, awaitItem().single().displayTitle)
        }
    }

    @Test
    fun `observeChats falls back to localized untitled when upstream sentinel title is provided`() = runTest {
        source.value = listOf(chat(chatId = "abc", title = "Untitled Chat"))

        repository.observeChats().test {
            assertEquals(FALLBACK, awaitItem().single().displayTitle)
        }
    }

    @Test
    fun `observeChats derives ImageGeneration when model is image-generation`() = runTest {
        source.value = listOf(chat(chatId = "abc", title = "Image", model = "image-generation"))

        repository.observeChats().test {
            assertEquals(ChatType.ImageGeneration, awaitItem().single().type)
        }
    }

    @Test
    fun `observeChats derives Voice when model is voice-mode`() = runTest {
        source.value = listOf(chat(chatId = "abc", title = "Voice", model = "voice-mode"))

        repository.observeChats().test {
            assertEquals(ChatType.Voice, awaitItem().single().type)
        }
    }

    @Test
    fun `observeChats derives Discussion fallback when model is unknown`() = runTest {
        source.value = listOf(chat(chatId = "abc", title = "Just text", model = "gpt-5-mini"))

        repository.observeChats().test {
            assertEquals(ChatType.Discussion, awaitItem().single().type)
        }
    }

    @Test
    fun `observeChats derives Discussion fallback when model is empty`() = runTest {
        source.value = listOf(chat(chatId = "abc", title = "Just text", model = ""))

        repository.observeChats().test {
            assertEquals(ChatType.Discussion, awaitItem().single().type)
        }
    }

    @Test
    fun `observeChats parses lastEdit ISO-8601 to millis`() = runTest {
        source.value = listOf(chat(chatId = "abc", title = "x", lastEdit = "2026-04-01T21:31:54.260Z"))

        repository.observeChats().test {
            val expected = java.time.Instant.parse("2026-04-01T21:31:54.260Z").toEpochMilli()
            assertEquals(expected, awaitItem().single().lastEditMillis)
        }
    }

    @Test
    fun `observeChats yields zero millis when lastEdit is invalid`() = runTest {
        source.value = listOf(chat(chatId = "abc", title = "x", lastEdit = "not a date"))

        repository.observeChats().test {
            assertEquals(0L, awaitItem().single().lastEditMillis)
        }
    }

    @Test
    fun `observeChats re-emits on source change`() = runTest {
        source.value = listOf(chat(chatId = "a", title = "First"))

        repository.observeChats().test {
            assertEquals(1, awaitItem().size)
            source.value = emptyList()
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun `deleteChat delegates to store`() = runTest {
        repository.deleteChat("abc")
        verify(chatStore).deleteChat("abc")
    }

    @Test
    fun `deleteAllChats delegates to store`() = runTest {
        repository.deleteAllChats()
        verify(chatStore).deleteAllChats()
    }

    private fun chat(
        chatId: String,
        title: String = "Title",
        model: String = "gpt-5-mini",
        lastEdit: String = "2026-04-01T00:00:00.000Z",
        pinned: Boolean = false,
        fileRefs: List<String> = emptyList(),
    ): DuckAiChat = DuckAiChat(
        chatId = chatId,
        title = title,
        model = model,
        lastEdit = lastEdit,
        pinned = pinned,
        fileRefs = fileRefs,
    )

    private companion object {
        const val FALLBACK = "Untitled chat"
    }
}
