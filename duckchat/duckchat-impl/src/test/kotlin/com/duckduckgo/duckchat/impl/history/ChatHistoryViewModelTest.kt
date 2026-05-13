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

import app.cash.turbine.TurbineTestContext
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
    private val viewModel = ChatHistoryViewModel(repository, coroutineRule.testScope)

    @Test
    fun `initial state is Loading`() = coroutineRule.testScope.runTest {
        assertEquals(ChatHistoryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `empty source emits Empty state`() = coroutineRule.testScope.runTest {
        viewModel.uiState.test {
            assertEquals(ChatHistoryUiState.Empty, awaitInitialNonLoading())
        }
    }

    @Test
    fun `non-empty source emits Loaded with pinned and recent partition`() = coroutineRule.testScope.runTest {
        source.value = listOf(
            item("a", pinned = false, lastEdit = 100L),
            item("b", pinned = true, lastEdit = 200L),
            item("c", pinned = false, lastEdit = 300L),
        )

        viewModel.uiState.test {
            val loaded = awaitInitialLoaded()
            assertEquals(listOf("b"), loaded.pinned.map { it.chatId })
            assertEquals(listOf("c", "a"), loaded.recent.map { it.chatId })
        }
    }

    @Test
    fun `Loaded state defaults to Default mode with no search and no confirmation`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a"))

        viewModel.uiState.test {
            val loaded = awaitInitialLoaded()
            assertEquals(ChatHistoryUiState.Mode.Default, loaded.mode)
            assertEquals("", loaded.searchQuery)
            assertEquals(false, loaded.searchActive)
            assertEquals(null, loaded.confirmation)
        }
    }

    @Test
    fun `pinned and recent each sort independently by date descending`() = coroutineRule.testScope.runTest {
        source.value = listOf(
            item("p1", pinned = true, lastEdit = 100L),
            item("p2", pinned = true, lastEdit = 300L),
            item("r1", pinned = false, lastEdit = 200L),
            item("r2", pinned = false, lastEdit = 400L),
        )

        viewModel.uiState.test {
            val loaded = awaitInitialLoaded()
            assertEquals(listOf("p2", "p1"), loaded.pinned.map { it.chatId })
            assertEquals(listOf("r2", "r1"), loaded.recent.map { it.chatId })
        }
    }

    @Test
    fun `transition from Loaded to Empty when source clears`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            source.value = emptyList()
            assertEquals(ChatHistoryUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `onSearchActivated marks Loaded as searchActive without filtering`() = coroutineRule.testScope.runTest {
        source.value = listOf(
            item("a", title = "Apple"),
            item("b", title = "Banana"),
        )

        viewModel.uiState.test {
            assertEquals(false, awaitInitialLoaded().searchActive)

            viewModel.onSearchActivated()

            val activated = awaitItem() as Loaded
            assertEquals(true, activated.searchActive)
            assertEquals("", activated.searchQuery)
            assertEquals(listOf("a", "b"), activated.recent.map { it.chatId })
        }
    }

    @Test
    fun `onSearchQueryChanged filters recent by case-insensitive substring`() = coroutineRule.testScope.runTest {
        source.value = listOf(
            item("a", title = "Apple pie"),
            item("b", title = "Banana bread"),
            item("c", title = "Pineapple crumble"),
        )

        viewModel.uiState.test {
            awaitInitialLoaded()

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
    fun `onSearchQueryChanged filters pinned section independently of recent`() = coroutineRule.testScope.runTest {
        source.value = listOf(
            item("p1", pinned = true, title = "Pinned apple"),
            item("p2", pinned = true, title = "Pinned banana"),
            item("r1", title = "Recent apple"),
            item("r2", title = "Recent banana"),
        )

        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onSearchActivated()
            awaitItem() // searchActive=true

            viewModel.onSearchQueryChanged("apple")
            val filtered = awaitItem() as Loaded
            assertEquals(listOf("p1"), filtered.pinned.map { it.chatId })
            assertEquals(listOf("r1"), filtered.recent.map { it.chatId })
        }
    }

    @Test
    fun `query with no matches keeps Loaded with empty sections so search UI stays visible`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a", title = "Apple"))

        viewModel.uiState.test {
            awaitInitialLoaded()

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
    fun `onSearchClosed resets searchActive and searchQuery to restore unfiltered list`() = coroutineRule.testScope.runTest {
        source.value = listOf(
            item("a", title = "Apple"),
            item("b", title = "Banana"),
        )

        viewModel.uiState.test {
            awaitInitialLoaded()

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

    // --- Fire-all ---

    @Test
    fun `onFireAllRequested with two or more Recent chats sets FireAll confirmation with the Recent count`() = runTest {
        source.value = listOf(
            item("p", pinned = true),
            item("r1"),
            item("r2"),
            item("r3"),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded

            viewModel.onFireAllRequested()

            val confirming = awaitItem() as Loaded
            assertEquals(ChatHistoryUiState.PendingConfirmation.FireAll(count = 3), confirming.confirmation)
            assertTrue(repository.deletedChatIds.isEmpty())
        }
    }

    @Test
    fun `onFireAllRequested with exactly one Recent chat deletes immediately without setting confirmation`() = runTest {
        source.value = listOf(
            item("p", pinned = true),
            item("r1"),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded

            viewModel.onFireAllRequested()

            val afterDelete = awaitItem() as Loaded
            assertEquals(null, afterDelete.confirmation)
            assertEquals(listOf("r1"), repository.deletedChatIds)
            assertEquals(listOf("p"), afterDelete.pinned.map { it.chatId })
            assertEquals(emptyList<String>(), afterDelete.recent.map { it.chatId })
        }
    }

    @Test
    fun `onFireAllRequested with no Recent chats is a no-op`() = runTest {
        source.value = listOf(
            item("p1", pinned = true),
            item("p2", pinned = true),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            val initial = awaitItem() as Loaded
            assertEquals(null, initial.confirmation)

            viewModel.onFireAllRequested()

            expectNoEvents()
            assertTrue(repository.deletedChatIds.isEmpty())
        }
    }

    @Test
    fun `onFireAllConfirmed only clears the confirmation state — dialog options path handles deletion`() = runTest {
        source.value = listOf(
            item("p1", pinned = true),
            item("p2", pinned = true),
            item("r1"),
            item("r2"),
            item("r3"),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded

            viewModel.onFireAllRequested()
            awaitItem() // confirmation = FireAll(3)

            viewModel.onFireAllConfirmed()

            // ViewModel doesn't touch the repository — the dialog drives the deletion.
            val cleared = awaitItem() as Loaded
            assertEquals(null, cleared.confirmation)
            assertTrue(repository.deletedChatIds.isEmpty())
            assertEquals(listOf("p1", "p2"), cleared.pinned.map { it.chatId })
            assertEquals(listOf("r1", "r2", "r3"), cleared.recent.map { it.chatId })
        }
    }

    @Test
    fun `onConfirmationCancelled clears the FireAll confirmation without deleting`() = runTest {
        source.value = listOf(
            item("r1"),
            item("r2"),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded

            viewModel.onFireAllRequested()
            val confirming = awaitItem() as Loaded
            assertEquals(ChatHistoryUiState.PendingConfirmation.FireAll(count = 2), confirming.confirmation)

            viewModel.onConfirmationCancelled()

            val cancelled = awaitItem() as Loaded
            assertEquals(null, cancelled.confirmation)
            assertTrue(repository.deletedChatIds.isEmpty())
            assertEquals(listOf("r1", "r2"), cancelled.recent.map { it.chatId })
        }
    }

    @Test
    fun `onFireAllConfirmed does not call the repository directly — dialog drives the deletion`() = runTest {
        source.value = listOf(
            item("p", pinned = true),
            item("r1"),
            item("r2"),
        )

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded

            viewModel.onFireAllRequested()
            awaitItem() // confirmation = FireAll(2)
            viewModel.onFireAllConfirmed()
            awaitItem() // confirmation cleared
        }

        // ViewModel never touches the repository on the dialog path — production wipes Pinned too.
        assertEquals(false, repository.deleteAllChatsCalled)
        assertTrue(repository.deletedChatIds.isEmpty())
    }

    /**
     * `stateIn(WhileSubscribed)` does not guarantee subscribers observe the `Loading` initial
     * value — the upstream may emit before the StateFlow can replay it. Tolerate both orderings.
     */
    private suspend fun TurbineTestContext<ChatHistoryUiState>.awaitInitialLoaded(): Loaded {
        val first = awaitItem()
        return (first as? Loaded) ?: (awaitItem() as Loaded)
    }

    private suspend fun TurbineTestContext<ChatHistoryUiState>.awaitInitialNonLoading(): ChatHistoryUiState {
        val first = awaitItem()
        return if (first is ChatHistoryUiState.Loading) awaitItem() else first
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
    private val source: MutableStateFlow<List<ChatHistoryItem>>,
) : ChatHistoryRepository {
    val deletedChatIds: MutableList<String> = mutableListOf()
    var deleteAllChatsCalled: Boolean = false
        private set

    override fun observeChats(): Flow<List<ChatHistoryItem>> = source

    override suspend fun deleteChat(chatId: String) {
        deletedChatIds += chatId
        source.value = source.value.filterNot { it.chatId == chatId }
    }

    override suspend fun deleteAllChats() {
        deleteAllChatsCalled = true
        source.value = emptyList()
    }
}
