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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RenameChatViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val repository = RecordingRenameRepository()
    private val viewModel = RenameChatViewModel(repository, coroutineRule.testScope)

    @Test
    fun `onSaveClicked forwards trimmed title and emits Success`() = coroutineRule.testScope.runTest {
        viewModel.results.test {
            viewModel.onSaveClicked("chat-1", "  New Title  ")

            assertEquals(RenameChatViewModel.RenameResult.Success, awaitItem())
            assertEquals(listOf("chat-1" to "New Title"), repository.renames)
        }
    }

    @Test
    fun `onSaveClicked emits Error when repository reports failure`() = coroutineRule.testScope.runTest {
        repository.nextResult = false

        viewModel.results.test {
            viewModel.onSaveClicked("chat-1", "New Title")

            assertEquals(RenameChatViewModel.RenameResult.Error, awaitItem())
        }
    }

    @Test
    fun `onSaveClicked emits Error when repository throws`() = coroutineRule.testScope.runTest {
        repository.errorToThrow = IllegalStateException("boom")

        viewModel.results.test {
            viewModel.onSaveClicked("chat-1", "New Title")

            assertEquals(RenameChatViewModel.RenameResult.Error, awaitItem())
        }
    }
}

private class RecordingRenameRepository : ChatHistoryRepository {
    val renames: MutableList<Pair<String, String>> = mutableListOf()
    var errorToThrow: Throwable? = null
    var nextResult: Boolean = true

    override fun observeChats(): Flow<List<ChatHistoryItem>> = MutableStateFlow(emptyList())

    override suspend fun deleteChat(chatId: String) = Unit
    override suspend fun deleteAllChats() = Unit
    override suspend fun exportChat(chatId: String): java.io.File = java.io.File("/tmp/noop.txt")

    override suspend fun renameChat(chatId: String, newTitle: String): Boolean {
        errorToThrow?.let { throw it }
        renames += chatId to newTitle
        return nextResult
    }

    override suspend fun setPinned(chatId: String, pinned: Boolean) = Unit
}
