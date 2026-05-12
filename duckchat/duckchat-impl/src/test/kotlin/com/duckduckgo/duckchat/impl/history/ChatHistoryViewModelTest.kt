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

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.Loaded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatHistoryViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val source = MutableStateFlow<List<ChatHistoryItem>>(emptyList())
    private val repository = FakeChatHistoryRepository(source)
    private val viewModel = ChatHistoryViewModel(repository)

    @Test
    fun `initial state is Loading`() = runTest {
        assertEquals(ChatHistoryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `empty source emits Empty state`() = runTest {
        viewModel.uiState.test {
            assertEquals(ChatHistoryUiState.Loading, awaitItem())
            assertEquals(ChatHistoryUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `non-empty source emits Loaded with pinned and recent partition`() = runTest {
        source.value = listOf(
            item("a", pinned = false, lastEdit = 100L),
            item("b", pinned = true, lastEdit = 200L),
            item("c", pinned = false, lastEdit = 300L),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            val loaded = awaitItem() as ChatHistoryUiState.Loaded
            assertEquals(listOf("b"), loaded.pinned.map { it.chatId })
            assertEquals(listOf("c", "a"), loaded.recent.map { it.chatId })
        }
    }

    @Test
    fun `Loaded state defaults to Default mode with no search and no confirmation`() = runTest {
        source.value = listOf(item("a"))

        viewModel.uiState.test {
            awaitItem() // Loading
            val loaded = awaitItem() as ChatHistoryUiState.Loaded
            assertEquals(ChatHistoryUiState.Mode.Default, loaded.mode)
            assertEquals("", loaded.searchQuery)
            assertEquals(false, loaded.searchActive)
            assertEquals(null, loaded.confirmation)
        }
    }

    @Test
    fun `pinned and recent each sort independently by date descending`() = runTest {
        source.value = listOf(
            item("p1", pinned = true, lastEdit = 100L),
            item("p2", pinned = true, lastEdit = 300L),
            item("r1", pinned = false, lastEdit = 200L),
            item("r2", pinned = false, lastEdit = 400L),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            val loaded = awaitItem() as ChatHistoryUiState.Loaded
            assertEquals(listOf("p2", "p1"), loaded.pinned.map { it.chatId })
            assertEquals(listOf("r2", "r1"), loaded.recent.map { it.chatId })
        }
    }

    @Test
    fun `transition from Loaded to Empty when source clears`() = runTest {
        source.value = listOf(item("a"))

        viewModel.uiState.test {
            awaitItem() // Loading
            assertTrue(awaitItem() is ChatHistoryUiState.Loaded)
            source.value = emptyList()
            assertEquals(ChatHistoryUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `onSearchActivated marks Loaded as searchActive without filtering`() = runTest {
        source.value = listOf(
            item("a", title = "Apple"),
            item("b", title = "Banana"),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            assertEquals(false, (awaitItem() as Loaded).searchActive)

            viewModel.onSearchActivated()

            val activated = awaitItem() as Loaded
            assertEquals(true, activated.searchActive)
            assertEquals("", activated.searchQuery)
            assertEquals(listOf("a", "b"), activated.recent.map { it.chatId })
        }
    }

    @Test
    fun `onSearchQueryChanged filters recent by case-insensitive substring`() = runTest {
        source.value = listOf(
            item("a", title = "Apple pie"),
            item("b", title = "Banana bread"),
            item("c", title = "Pineapple crumble"),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded

            viewModel.onSearchActivated()
            awaitItem() // searchActive=true

            viewModel.onSearchQueryChanged("apple")
            val filtered = awaitItem() as Loaded
            assertEquals("apple", filtered.searchQuery)
            assertEquals(listOf("a", "c"), filtered.recent.map { it.chatId })
            assertEquals(emptyList<String>(), filtered.pinned.map { it.chatId })
        }
    }

    @Test
    fun `onSearchQueryChanged filters pinned section independently of recent`() = runTest {
        source.value = listOf(
            item("p1", pinned = true, title = "Pinned apple"),
            item("p2", pinned = true, title = "Pinned banana"),
            item("r1", title = "Recent apple"),
            item("r2", title = "Recent banana"),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded

            viewModel.onSearchActivated()
            awaitItem() // searchActive=true

            viewModel.onSearchQueryChanged("apple")
            val filtered = awaitItem() as Loaded
            assertEquals(listOf("p1"), filtered.pinned.map { it.chatId })
            assertEquals(listOf("r1"), filtered.recent.map { it.chatId })
        }
    }

    @Test
    fun `query with no matches keeps Loaded with empty sections so search UI stays visible`() = runTest {
        source.value = listOf(item("a", title = "Apple"))

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded

            viewModel.onSearchActivated()
            awaitItem()

            viewModel.onSearchQueryChanged("zzz")
            val filtered = awaitItem() as Loaded
            assertEquals(true, filtered.searchActive)
            assertEquals("zzz", filtered.searchQuery)
            assertTrue(filtered.pinned.isEmpty())
            assertTrue(filtered.recent.isEmpty())
        }
    }

    @Test
    fun `onSearchClosed resets searchActive and searchQuery to restore unfiltered list`() = runTest {
        source.value = listOf(
            item("a", title = "Apple"),
            item("b", title = "Banana"),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded

            viewModel.onSearchActivated()
            awaitItem()
            viewModel.onSearchQueryChanged("apple")
            awaitItem()

            viewModel.onSearchClosed()
            val restored = awaitItem() as Loaded
            assertEquals(false, restored.searchActive)
            assertEquals("", restored.searchQuery)
            assertEquals(listOf("a", "b"), restored.recent.map { it.chatId })
        }
    }

    private fun item(
        chatId: String,
        pinned: Boolean = false,
        lastEdit: Long = 0L,
        title: String = chatId,
    ): ChatHistoryItem = ChatHistoryItem(
        chatId = chatId,
        displayTitle = title,
        type = ChatType.Discussion,
        pinned = pinned,
        lastEditMillis = lastEdit,
    )
}

private class FakeChatHistoryRepository(
    private val source: Flow<List<ChatHistoryItem>>,
) : ChatHistoryRepository {
    override fun observeChats(): Flow<List<ChatHistoryItem>> = source
    override suspend fun deleteChat(chatId: String) = Unit
    override suspend fun deleteAllChats() = Unit
}
