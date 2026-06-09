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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.models.ModelDisplay
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class RenameChatViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val repository = RecordingRenameRepository()
    private val pixel: Pixel = mock()
    private val viewModel = RenameChatViewModel(repository, coroutineRule.testScope, pixel)

    @Test
    fun `onSaveClicked forwards trimmed title and emits Success`() = coroutineRule.testScope.runTest {
        viewModel.results.test {
            viewModel.onSaveClicked("chat-1", "  New Title  ")

            assertEquals(RenameChatViewModel.RenameResult.Success, awaitItem())
            assertEquals(listOf("chat-1" to "New Title"), repository.renames)
        }
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_HISTORY_RENAME_SAVED_COUNT)
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_HISTORY_RENAME_SAVED_DAILY, type = Daily())
    }

    @Test
    fun `onSaveClicked emits Error when repository reports failure`() = coroutineRule.testScope.runTest {
        repository.nextResult = false

        viewModel.results.test {
            viewModel.onSaveClicked("chat-1", "New Title")

            assertEquals(RenameChatViewModel.RenameResult.Error, awaitItem())
        }
        verifyNoInteractions(pixel)
    }

    @Test
    fun `onSaveClicked emits Error when repository throws`() = coroutineRule.testScope.runTest {
        repository.errorToThrow = IllegalStateException("boom")

        viewModel.results.test {
            viewModel.onSaveClicked("chat-1", "New Title")

            assertEquals(RenameChatViewModel.RenameResult.Error, awaitItem())
        }
        verifyNoInteractions(pixel)
    }
}

private class RecordingRenameRepository : ChatHistoryRepository {
    val renames: MutableList<Pair<String, String>> = mutableListOf()
    var errorToThrow: Throwable? = null
    var nextResult: Boolean = true

    override fun observeChats(): Flow<List<ChatHistoryItem>> = MutableStateFlow(emptyList())

    override suspend fun deleteChat(chatId: String) = Unit
    override suspend fun deleteAllChats() = Unit
    override suspend fun exportChat(chatId: String, modelDisplay: ModelDisplay?): java.io.File = java.io.File("/tmp/noop.txt")
    override suspend fun exportChats(requests: List<ChatExportRequest>): List<java.io.File> = requests.map { java.io.File("/tmp/noop.txt") }

    override suspend fun renameChat(chatId: String, newTitle: String): Boolean {
        errorToThrow?.let { throw it }
        renames += chatId to newTitle
        return nextResult
    }

    override suspend fun setPinned(chatId: String, pinned: Boolean) = Unit
}
