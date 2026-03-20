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

package com.duckduckgo.duckchat.localserver.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiChatsDao
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RealDuckAiChatStorageTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dao: DuckAiChatsDao = mock()
    private val storage by lazy {
        RealDuckAiChatStorage(dao, Lazy { tempFolder.root }, coroutineRule.testDispatcherProvider)
    }

    private fun writeImage(uuid: String, chatId: String) {
        tempFolder.root.mkdirs()
        tempFolder.root.resolve(uuid).writeText("""{"uuid":"$uuid","chatId":"$chatId","data":"base64..."}""")
    }

    // --- deleteChat ---

    @Test
    fun `deleteChat deletes chat from DAO`() = runTest {
        storage.deleteChat("chat-1")
        verify(dao).delete("chat-1")
    }

    @Test
    fun `deleteChat removes associated image files`() = runTest {
        writeImage("img-1", "chat-1")
        writeImage("img-2", "chat-1")

        storage.deleteChat("chat-1")

        assertFalse(tempFolder.root.resolve("img-1").exists())
        assertFalse(tempFolder.root.resolve("img-2").exists())
    }

    @Test
    fun `deleteChat does not remove images belonging to other chats`() = runTest {
        writeImage("img-1", "chat-1")
        writeImage("img-2", "chat-2")

        storage.deleteChat("chat-1")

        assertFalse(tempFolder.root.resolve("img-1").exists())
        assertTrue(tempFolder.root.resolve("img-2").exists())
    }

    @Test
    fun `deleteChat is safe when images directory does not exist`() = runTest {
        // tempFolder.root exists but is empty — simulate missing dir by using a subdir that was never created
        val storage = RealDuckAiChatStorage(dao, Lazy { tempFolder.root.resolve("nonexistent") }, coroutineRule.testDispatcherProvider)
        storage.deleteChat("chat-1")
        verify(dao).delete("chat-1")
    }

    @Test
    fun `deleteChat is safe when chat has no associated images`() = runTest {
        writeImage("img-1", "chat-2")

        storage.deleteChat("chat-1")

        verify(dao).delete("chat-1")
        assertTrue(tempFolder.root.resolve("img-1").exists())
    }

    // --- deleteAllChats ---

    @Test
    fun `deleteAllChats deletes all chats from DAO`() = runTest {
        storage.deleteAllChats()
        verify(dao).deleteAll()
    }

    @Test
    fun `deleteAllChats removes all image files`() = runTest {
        writeImage("img-1", "chat-1")
        writeImage("img-2", "chat-2")

        storage.deleteAllChats()

        assertEquals(0, tempFolder.root.listFiles()?.size ?: 0)
    }

    @Test
    fun `deleteAllChats is safe when no images exist`() = runTest {
        storage.deleteAllChats()
        verify(dao).deleteAll()
    }

    @Test
    fun `deleteAllChats is safe when images directory does not exist`() = runTest {
        val storage = RealDuckAiChatStorage(dao, Lazy { tempFolder.root.resolve("nonexistent") }, coroutineRule.testDispatcherProvider)
        storage.deleteAllChats()
        verify(dao).deleteAll()
    }
}
