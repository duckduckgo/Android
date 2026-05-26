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
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingTrigger
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.Loaded
import com.duckduckgo.duckchat.impl.messaging.fakes.FakeDuckChatInternal
import com.duckduckgo.duckchat.impl.models.ChatType
import com.duckduckgo.duckchat.impl.models.ModelDisplay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChatHistoryViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val source = MutableStateFlow<List<ChatHistoryItem>>(emptyList())
    private val repository = FakeChatHistoryRepository(source)
    private val duckChat = FakeDuckChatInternal()
    private val dataClearingTrigger = RecordingDataClearingTrigger()
    private val showClearDuckAIChatHistoryFlow = MutableStateFlow(true)
    private val duckAiFeatureState: DuckAiFeatureState = mock {
        whenever(it.showClearDuckAIChatHistory).thenReturn(showClearDuckAIChatHistoryFlow)
    }
    private val viewModel = ChatHistoryViewModel(repository, coroutineRule.testScope, duckChat, dataClearingTrigger, duckAiFeatureState)

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
    fun `onFireAllRequested with two or more chats sets FireAll confirmation with every chatId including pinned`() = runTest {
        source.value = listOf(
            item("p", pinned = true),
            item("r1"),
            item("r2"),
            item("r3"),
        )

        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onFireAllRequested()

            val confirming = awaitItem() as Loaded
            assertEquals(
                ChatHistoryUiState.PendingConfirmation.FireAll(chatIds = setOf("p", "r1", "r2", "r3")),
                confirming.confirmation,
            )
            assertTrue(repository.deletedChatIds.isEmpty())
        }
    }

    @Test
    fun `onFireAllRequested with exactly one recent chat sets FireAll confirmation`() = runTest {
        source.value = listOf(item("r1"))

        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onFireAllRequested()

            val confirming = awaitItem() as Loaded
            assertEquals(
                ChatHistoryUiState.PendingConfirmation.FireAll(chatIds = setOf("r1")),
                confirming.confirmation,
            )
        }
        assertTrue(dataClearingTrigger.calls.isEmpty())
        assertTrue(repository.deletedChatIds.isEmpty())
    }

    @Test
    fun `onFireAllRequested with exactly one pinned chat sets FireAll confirmation`() = runTest {
        source.value = listOf(item("p", pinned = true))

        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onFireAllRequested()

            val confirming = awaitItem() as Loaded
            assertEquals(
                ChatHistoryUiState.PendingConfirmation.FireAll(chatIds = setOf("p")),
                confirming.confirmation,
            )
        }
        assertTrue(dataClearingTrigger.calls.isEmpty())
        assertTrue(repository.deletedChatIds.isEmpty())
    }

    @Test
    fun `onFireAllRequested with only pinned chats and no recent sets FireAll confirmation with the pinned ids`() = runTest {
        source.value = listOf(
            item("p1", pinned = true),
            item("p2", pinned = true),
        )

        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onFireAllRequested()

            val confirming = awaitItem() as Loaded
            assertEquals(
                ChatHistoryUiState.PendingConfirmation.FireAll(chatIds = setOf("p1", "p2")),
                confirming.confirmation,
            )
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
            awaitInitialLoaded()

            viewModel.onFireAllRequested()
            awaitItem() // confirmation = FireAll(5) — p1, p2, r1, r2, r3

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
            awaitInitialLoaded()

            viewModel.onFireAllRequested()
            val confirming = awaitItem() as Loaded
            assertEquals(ChatHistoryUiState.PendingConfirmation.FireAll(chatIds = setOf("r1", "r2")), confirming.confirmation)

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
            awaitInitialLoaded()

            viewModel.onFireAllRequested()
            awaitItem() // confirmation = FireAll(3) — p, r1, r2
            viewModel.onFireAllConfirmed()
            awaitItem() // confirmation cleared
        }

        // ViewModel never touches the repository on the dialog path — production wipes every chat
        // including Pinned via the dialog-driven Selected dispatch.
        assertEquals(false, repository.deleteAllChatsCalled)
        assertTrue(repository.deletedChatIds.isEmpty())
    }

    // --- Chat resume / Duck.ai open ---

    @Test
    fun `onChatRowClicked in default mode resumes the chat in DuckAi`() = runTest {
        viewModel.onChatRowClicked("abc")

        assertEquals(listOf("abc"), duckChat.openWithChatIdCalls)
    }

    @Test
    fun `onChatRowLongClicked in default mode enters select mode with the row pre-selected`() = runTest {
        source.value = listOf(item("a"), item("b"))

        viewModel.uiState.test {
            awaitInitialLoaded()

            val consumed = viewModel.onChatRowLongClicked("a")

            assertTrue(consumed)
            val loaded = awaitItem() as Loaded
            val mode = loaded.mode as ChatHistoryUiState.Mode.Selecting
            assertEquals(setOf("a"), mode.selectedChatIds)
            assertTrue(duckChat.openWithChatIdCalls.isEmpty())
        }
    }

    @Test
    fun `onChatRowLongClicked in select mode toggles the row like a tap`() = runTest {
        source.value = listOf(item("a"), item("b"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onChatRowLongClicked("a")
            awaitItem() // Selecting({a})

            val consumed = viewModel.onChatRowLongClicked("b")

            assertTrue(consumed)
            val loaded = awaitItem() as Loaded
            val mode = loaded.mode as ChatHistoryUiState.Mode.Selecting
            assertEquals(setOf("a", "b"), mode.selectedChatIds)
        }
    }

    @Test
    fun `onChatRowClicked in select mode toggles selection instead of opening DuckAi`() = runTest {
        source.value = listOf(item("a"))
        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem()

            viewModel.onChatRowClicked("a")

            val loaded = awaitItem() as Loaded
            val mode = loaded.mode as ChatHistoryUiState.Mode.Selecting
            assertEquals(setOf("a"), mode.selectedChatIds)
            assertTrue(duckChat.openWithChatIdCalls.isEmpty())
        }
    }

    @Test
    fun `onOpenDuckAiClicked delegates to DuckChat`() = runTest {
        viewModel.onOpenDuckAiClicked()

        assertEquals(1, duckChat.openDuckChatCalls)
    }

    @Test
    fun `onFireIconClicked in default mode triggers Fire-all`() = runTest {
        source.value = listOf(item("r1"), item("r2"))
        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onFireIconClicked()

            val confirming = awaitItem() as Loaded
            assertEquals(ChatHistoryUiState.PendingConfirmation.FireAll(chatIds = setOf("r1", "r2")), confirming.confirmation)
        }
    }

    @Test
    fun `onFireIconClicked in select mode triggers Delete-selected`() = runTest {
        source.value = listOf(item("a"), item("b"), item("c"))
        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem()
            viewModel.onSelectionToggled("a")
            awaitItem()
            viewModel.onSelectionToggled("b")
            awaitItem()

            viewModel.onFireIconClicked()

            val confirming = awaitItem() as Loaded
            val confirmation = confirming.confirmation as ChatHistoryUiState.PendingConfirmation.DeleteSelected
            assertEquals(setOf("a", "b"), confirmation.chatIds)
        }
    }

    // --- Select-mode ---

    @Test
    fun `onEnterSelectMode transitions to Selecting with empty selection`() = runTest {
        source.value = listOf(item("a"), item("b"))

        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onEnterSelectMode()

            val loaded = awaitItem() as Loaded
            val mode = loaded.mode as ChatHistoryUiState.Mode.Selecting
            assertEquals(emptySet<String>(), mode.selectedChatIds)
        }
    }

    @Test
    fun `onSelectionToggled adds and removes ids and empty selection stays in select mode`() = runTest {
        source.value = listOf(item("a"), item("b"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem() // Selecting({})

            viewModel.onSelectionToggled("a")
            val afterAdd = awaitItem() as Loaded
            assertEquals(setOf("a"), (afterAdd.mode as ChatHistoryUiState.Mode.Selecting).selectedChatIds)

            viewModel.onSelectionToggled("a")
            val afterRemove = awaitItem() as Loaded
            val mode = afterRemove.mode as ChatHistoryUiState.Mode.Selecting
            assertEquals(emptySet<String>(), mode.selectedChatIds)
        }
    }

    @Test
    fun `onSelectAllToggled fills the selection with every visible chat id`() = runTest {
        source.value = listOf(item("p", pinned = true), item("a"), item("b"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem() // Selecting({})

            viewModel.onSelectAllToggled()

            val loaded = awaitItem() as Loaded
            val mode = loaded.mode as ChatHistoryUiState.Mode.Selecting
            assertEquals(setOf("p", "a", "b"), mode.selectedChatIds)
        }
    }

    @Test
    fun `onSelectAllToggled with everything selected clears the selection but stays in select mode`() = runTest {
        source.value = listOf(item("a"), item("b"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem() // Selecting({})
            viewModel.onSelectAllToggled()
            awaitItem() // Selecting({a, b})

            viewModel.onSelectAllToggled()

            val cleared = awaitItem() as Loaded
            val mode = cleared.mode as ChatHistoryUiState.Mode.Selecting
            assertEquals(emptySet<String>(), mode.selectedChatIds)
        }
    }

    @Test
    fun `when an item disappears from the source the selection drops the stale id in the next state`() = runTest {
        source.value = listOf(item("a"), item("b"), item("c"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem() // Selecting({})
            viewModel.onSelectionToggled("a")
            awaitItem() // Selecting({a})
            viewModel.onSelectionToggled("b")
            awaitItem() // Selecting({a, b})

            source.value = listOf(item("a"), item("c"))

            val updated = awaitItem() as Loaded
            val mode = updated.mode as ChatHistoryUiState.Mode.Selecting
            assertEquals(setOf("a"), mode.selectedChatIds)
        }
    }

    @Test
    fun `onSelectAllToggled with a stale selection equal to visible still toggles off`() = runTest {
        source.value = listOf(item("a"), item("b"), item("c"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem() // Selecting({})
            viewModel.onSelectAllToggled()
            awaitItem() // Selecting({a, b, c})

            source.value = listOf(item("a"), item("b"))
            awaitItem() // mode reconciled to Selecting({a, b}) by reduce

            viewModel.onSelectAllToggled()

            val cleared = awaitItem() as Loaded
            val mode = cleared.mode as ChatHistoryUiState.Mode.Selecting
            assertEquals(emptySet<String>(), mode.selectedChatIds)
        }
    }

    @Test
    fun `onSelectModeCancelled returns to Default mode with no deletion`() = runTest {
        source.value = listOf(item("a"), item("b"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem()
            viewModel.onSelectionToggled("a")
            awaitItem()

            viewModel.onSelectModeCancelled()

            val cancelled = awaitItem() as Loaded
            assertEquals(ChatHistoryUiState.Mode.Default, cancelled.mode)
            assertTrue(repository.deletedChatIds.isEmpty())
        }
    }

    @Test
    fun `onDeleteSelectedRequested with empty selection is a no-op`() = runTest {
        source.value = listOf(item("a"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem() // Selecting({})

            viewModel.onDeleteSelectedRequested()

            expectNoEvents()
            assertTrue(repository.deletedChatIds.isEmpty())
        }
    }

    @Test
    fun `onDeleteSelectedRequested with one selected dispatches DuckChats Selected and exits select mode`() = runTest {
        source.value = listOf(item("a"), item("b"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem()
            viewModel.onSelectionToggled("a")
            awaitItem()

            viewModel.onDeleteSelectedRequested()

            val final = awaitItem() as Loaded
            assertEquals(ChatHistoryUiState.Mode.Default, final.mode)
        }
        assertEquals(
            listOf(setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=a")))),
            dataClearingTrigger.calls,
        )
        assertTrue(repository.deletedChatIds.isEmpty())
    }

    @Test
    fun `onDeleteSelectedRequested with two or more sets DeleteSelected with the captured ids`() = runTest {
        source.value = listOf(item("a"), item("b"), item("c"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem()
            viewModel.onSelectionToggled("a")
            awaitItem()
            viewModel.onSelectionToggled("b")
            awaitItem()

            viewModel.onDeleteSelectedRequested()

            val confirming = awaitItem() as Loaded
            val confirmation = confirming.confirmation as ChatHistoryUiState.PendingConfirmation.DeleteSelected
            assertEquals(setOf("a", "b"), confirmation.chatIds)
            assertEquals(2, confirmation.count)
            assertTrue(repository.deletedChatIds.isEmpty())
        }
    }

    @Test
    fun `onDeleteSelectedConfirmed clears confirmation and exits select mode without dispatching`() = runTest {
        source.value = listOf(item("p", pinned = true), item("a"), item("b"), item("c"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem()
            viewModel.onSelectionToggled("a")
            awaitItem()
            viewModel.onSelectionToggled("c")
            awaitItem()
            viewModel.onDeleteSelectedRequested()
            awaitItem() // DeleteSelected({a, c})

            viewModel.onDeleteSelectedConfirmed()
            val final = awaitItem() as Loaded
            assertEquals(ChatHistoryUiState.Mode.Default, final.mode)
            assertEquals(null, final.confirmation)
        }
        // ViewModel must not dispatch — the dialog drives the clear via selectedChatUrls.
        assertTrue(dataClearingTrigger.calls.isEmpty())
        assertTrue(repository.deletedChatIds.isEmpty())
    }

    @Test
    fun `chatUrlsForDialog returns the captured chatIds mapped through DuckChat buildChatUrl`() = runTest {
        source.value = listOf(item("a"), item("b"), item("c"))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem()
            viewModel.onSelectionToggled("a")
            awaitItem()
            viewModel.onSelectionToggled("c")
            awaitItem()
            viewModel.onDeleteSelectedRequested()
            awaitItem() // DeleteSelected({a, c})

            assertEquals(
                setOf("https://duck.ai?chatID=a", "https://duck.ai?chatID=c"),
                viewModel.chatUrlsForDialog(),
            )
        }
    }

    @Test
    fun `chatUrlsForDialog returns null when no confirmation is pending`() = runTest {
        source.value = listOf(item("a"))

        viewModel.uiState.test {
            awaitInitialLoaded()

            assertEquals(null, viewModel.chatUrlsForDialog())
        }
    }

    @Test
    fun `chatUrlsForDialog returns every chat URL including pinned while a FireAll confirmation is pending`() = runTest {
        source.value = listOf(item("p", pinned = true), item("r1"), item("r2"))

        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onFireAllRequested()
            awaitItem() // FireAll({p, r1, r2})

            assertEquals(
                setOf("https://duck.ai?chatID=p", "https://duck.ai?chatID=r1", "https://duck.ai?chatID=r2"),
                viewModel.chatUrlsForDialog(),
            )
        }
    }

    // --- Per-row overflow Delete ---

    @Test
    fun `onDeleteSingleChat dispatches DuckChats Selected with that one chat url and no confirmation`() = runTest {
        source.value = listOf(item("a"), item("b"))

        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onDeleteSingleChat("a")

            expectNoEvents() // no confirmation state set
        }
        assertEquals(
            listOf(setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=a")))),
            dataClearingTrigger.calls,
        )
        assertTrue(repository.deletedChatIds.isEmpty())
    }

    @Test
    fun `onDeleteSingleChat is a no-op when showClearDuckAIChatHistory is off`() = runTest {
        source.value = listOf(item("a"))
        showClearDuckAIChatHistoryFlow.value = false

        viewModel.uiState.test {
            awaitInitialLoaded()

            viewModel.onDeleteSingleChat("a")

            expectNoEvents()
        }
        assertTrue(dataClearingTrigger.calls.isEmpty())
    }

    @Test
    fun `onDeleteSelectedRequested with one selection is a no-op when showClearDuckAIChatHistory is off`() = runTest {
        source.value = listOf(item("a"))
        showClearDuckAIChatHistoryFlow.value = false

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onEnterSelectMode()
            awaitItem() // Selecting({})
            viewModel.onSelectionToggled("a")
            awaitItem() // Selecting({a})

            viewModel.onDeleteSelectedRequested()

            awaitItem() // mode reset to Default before dispatch (which is gated off)
        }
        assertTrue(dataClearingTrigger.calls.isEmpty())
    }

    @Test
    fun `onDownloadRequested emits ShowDownloadComplete with the saved file name`() =
        coroutineRule.testScope.runTest {
            repository.exportResult = java.io.File("/tmp/duck.ai_2026-05-21_10-00-00.txt")

            viewModel.navigationEvents.test {
                viewModel.onDownloadRequested("chat-7")

                val event = awaitItem()
                assertTrue(event is ChatHistoryViewModel.NavigationEvent.ShowDownloadComplete)
                event as ChatHistoryViewModel.NavigationEvent.ShowDownloadComplete
                assertEquals("duck.ai_2026-05-21_10-00-00.txt", event.fileName)
            }
            assertEquals(listOf("chat-7"), repository.exportedChats)
        }

    @Test
    fun `onDownloadRequested emits ShowExportError when repository throws`() =
        coroutineRule.testScope.runTest {
            repository.exportError = IllegalStateException("boom")

            viewModel.navigationEvents.test {
                viewModel.onDownloadRequested("chat-7")

                assertEquals(ChatHistoryViewModel.NavigationEvent.ShowExportError, awaitItem())
            }
        }

    @Test
    fun `onRenameRequested emits OpenRename navigation event with chatId and currentTitle`() = coroutineRule.testScope.runTest {
        viewModel.navigationEvents.test {
            viewModel.onRenameRequested("chat-42", "My favourite chat")

            val event = awaitItem()
            assertTrue(event is ChatHistoryViewModel.NavigationEvent.OpenRename)
            event as ChatHistoryViewModel.NavigationEvent.OpenRename
            assertEquals("chat-42", event.chatId)
            assertEquals("My favourite chat", event.currentTitle)
        }
    }

    @Test
    fun `onTogglePin flips pinned to true when row was unpinned`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a", pinned = false))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onTogglePin("a")
            val updated = awaitItem() as Loaded
            assertEquals(listOf("a"), updated.pinned.map { it.chatId })
            assertEquals(emptyList<String>(), updated.recent.map { it.chatId })
        }
        assertEquals(listOf("a" to true), repository.pinnedChats)
    }

    @Test
    fun `onTogglePin flips pinned to false when row was pinned`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a", pinned = true))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onTogglePin("a")
            val updated = awaitItem() as Loaded
            assertEquals(emptyList<String>(), updated.pinned.map { it.chatId })
            assertEquals(listOf("a"), updated.recent.map { it.chatId })
        }
        assertEquals(listOf("a" to false), repository.pinnedChats)
    }

    @Test
    fun `onTogglePin is a no-op when chatId is unknown`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a", pinned = false))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onTogglePin("missing")
            expectNoEvents()
        }
        assertTrue(repository.pinnedChats.isEmpty())
    }

    @Test
    fun `onTogglePin emits PinToggled message event with previous pinned state false`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a", pinned = false))

        viewModel.messageEvents.test {
            // Drain the initial Loaded state so latestItems is primed.
            viewModel.uiState.test { awaitInitialLoaded() }
            viewModel.onTogglePin("a")

            val event = awaitItem()
            assertTrue(event is ChatHistoryViewModel.MessageEvent.PinToggled)
            event as ChatHistoryViewModel.MessageEvent.PinToggled
            assertEquals("a", event.chatId)
            assertEquals(false, event.wasPinned)
        }
    }

    @Test
    fun `onTogglePin emits PinToggled message event with previous pinned state true`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a", pinned = true))

        viewModel.messageEvents.test {
            viewModel.uiState.test { awaitInitialLoaded() }
            viewModel.onTogglePin("a")

            val event = awaitItem()
            assertTrue(event is ChatHistoryViewModel.MessageEvent.PinToggled)
            event as ChatHistoryViewModel.MessageEvent.PinToggled
            assertEquals("a", event.chatId)
            assertEquals(true, event.wasPinned)
        }
    }

    @Test
    fun `onTogglePin emits no message event when chatId is unknown`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a", pinned = false))

        viewModel.messageEvents.test {
            viewModel.uiState.test { awaitInitialLoaded() }
            viewModel.onTogglePin("missing")
            expectNoEvents()
        }
    }

    @Test
    fun `onUndoTogglePin restores the requested pinned state via the repository`() = coroutineRule.testScope.runTest {
        source.value = listOf(item("a", pinned = true))

        viewModel.uiState.test {
            awaitInitialLoaded()
            viewModel.onTogglePin("a") // a → unpinned
            awaitItem() // Loaded after the unpin write
            viewModel.onUndoTogglePin("a", restorePinned = true)
            val restored = awaitItem() as Loaded
            assertEquals(listOf("a"), restored.pinned.map { it.chatId })
            assertEquals(emptyList<String>(), restored.recent.map { it.chatId })
        }
        assertEquals(listOf("a" to false, "a" to true), repository.pinnedChats)
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
        model: String = "gpt-5-mini",
    ): ChatHistoryItem = ChatHistoryItem(
        chatId = chatId,
        displayTitle = title,
        type = ChatType.Discussion,
        model = model,
        pinned = pinned,
        lastEditMillis = lastEdit,
    )
}

private class FakeChatHistoryRepository(
    private val source: MutableStateFlow<List<ChatHistoryItem>>,
) : ChatHistoryRepository {
    val deletedChatIds: MutableList<String> = mutableListOf()
    val renamedChats: MutableList<Pair<String, String>> = mutableListOf()
    val pinnedChats: MutableList<Pair<String, Boolean>> = mutableListOf()
    val exportedChats: MutableList<String> = mutableListOf()
    var lastExportModelDisplay: ModelDisplay? = null
        private set
    var exportResult: java.io.File = java.io.File("/tmp/chat-export.txt")
    var exportError: Throwable? = null
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

    override suspend fun renameChat(chatId: String, newTitle: String): Boolean {
        renamedChats += chatId to newTitle
        return true
    }

    override suspend fun setPinned(chatId: String, pinned: Boolean) {
        pinnedChats += chatId to pinned
        source.value = source.value.map { if (it.chatId == chatId) it.copy(pinned = pinned) else it }
    }

    override suspend fun exportChat(chatId: String, modelDisplay: ModelDisplay?): java.io.File {
        exportedChats += chatId
        lastExportModelDisplay = modelDisplay
        exportError?.let { throw it }
        return exportResult
    }
}

private class RecordingDataClearingTrigger : DataClearingTrigger {
    val calls: MutableList<Set<ClearableData>> = mutableListOf()

    override suspend fun clearData(types: Set<ClearableData>) {
        calls += types
    }
}
