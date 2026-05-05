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

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaEntity
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

class RealDuckAiChatStoreTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val chatsDao: DuckAiBridgeChatsDao = mock()
    private val fileMetaDao: DuckAiBridgeFileMetaDao = mock()
    private val fakePrefs = InMemorySharedPreferences()
    private val migrationPrefs = DuckAiMigrationPrefs(
        mock<SharedPreferencesProvider>().also { whenever(it.getSharedPreferences(any(), any(), any())).thenReturn(fakePrefs) },
    )
    private lateinit var filesDir: File
    private lateinit var store: RealDuckAiChatStore

    @Before
    fun setup() {
        filesDir = Files.createTempDirectory("duck_ai_test").toFile()
        store = RealDuckAiChatStore(chatsDao, fileMetaDao, Lazy { filesDir }, coroutineTestRule.testDispatcherProvider, migrationPrefs)
    }

    // --- hasMigrated ---

    @Test
    fun `hasMigrated returns true when migration flag is set`() = runTest {
        fakePrefs.edit().putBoolean(DuckAiMigrationPrefs.CHATS_KEY, true).commit()
        assertTrue(store.hasMigrated())
    }

    @Test
    fun `hasMigrated returns false when migration flag is not set`() = runTest {
        assertFalse(store.hasMigrated())
    }

    // --- getChats ---

    @Test
    fun `getChats parses standard chat JSON`() = runTest {
        val json = """{"chatId":"abc","title":"Test","model":"gpt-5-mini","lastEdit":"2026-04-01T21:31:54.260Z","pinned":false}"""
        whenever(chatsDao.getAll()).thenReturn(listOf(DuckAiBridgeChatEntity("abc", json)))

        val chats = store.getChats()

        assertEquals(1, chats.size)
        assertEquals("abc", chats[0].chatId)
        assertEquals("Test", chats[0].title)
        assertEquals("gpt-5-mini", chats[0].model)
        assertEquals("2026-04-01T21:31:54.260Z", chats[0].lastEdit)
        assertFalse(chats[0].pinned)
        assertTrue(chats[0].fileRefs.isEmpty())
    }

    @Test
    fun `getChats parses fileRefs when present`() = runTest {
        val json = """
            {
              "chatId": "abc",
              "title": "Image",
              "model": "image-generation",
              "lastEdit": "2026-04-01T20:42:09.207Z",
              "pinned": false,
              "fileRefs": [
                "uuid1",
                "uuid2"
              ]
            }
        """.trimIndent()
        whenever(chatsDao.getAll()).thenReturn(listOf(DuckAiBridgeChatEntity("abc", json)))

        assertEquals(listOf("uuid1", "uuid2"), store.getChats()[0].fileRefs)
    }

    @Test
    fun `getChats uses Untitled Chat for missing title`() = runTest {
        val json = """{"chatId":"abc","model":"gpt-5-mini","lastEdit":"2026-04-01T21:31:54.260Z","pinned":false}"""
        whenever(chatsDao.getAll()).thenReturn(listOf(DuckAiBridgeChatEntity("abc", json)))

        assertEquals("Untitled Chat", store.getChats()[0].title)
    }

    @Test
    fun `getChats skips entries with malformed JSON`() = runTest {
        whenever(chatsDao.getAll()).thenReturn(listOf(DuckAiBridgeChatEntity("abc", "not json")))
        assertTrue(store.getChats().isEmpty())
    }

    @Test
    fun `getChats skips entries with empty chatId`() = runTest {
        val json = """{"chatId":"","title":"Test","model":"gpt-5-mini","lastEdit":"2026-04-01T21:31:54.260Z","pinned":false}"""
        whenever(chatsDao.getAll()).thenReturn(listOf(DuckAiBridgeChatEntity("abc", json)))
        assertTrue(store.getChats().isEmpty())
    }

    @Test
    fun `getChats returns empty list when store is empty`() = runTest {
        whenever(chatsDao.getAll()).thenReturn(emptyList())
        assertTrue(store.getChats().isEmpty())
    }

    // --- deleteChat ---

    @Test
    fun `deleteChat returns false when chat not found`() = runTest {
        whenever(chatsDao.getById("missing")).thenReturn(null)
        assertFalse(store.deleteChat("missing"))
    }

    @Test
    fun `deleteChat deletes chat row and returns true`() = runTest {
        val json = """{"chatId":"abc","title":"Test","model":"gpt-5-mini","lastEdit":"2026-04-01T21:31:54.260Z","pinned":false}"""
        whenever(chatsDao.getById("abc")).thenReturn(DuckAiBridgeChatEntity("abc", json))

        assertTrue(store.deleteChat("abc"))
        verify(chatsDao).delete("abc")
    }

    @Test
    fun `deleteChat deletes referenced files`() = runTest {
        val file1 = File(filesDir, "uuid1").also { it.writeText("data1") }
        val file2 = File(filesDir, "uuid2").also { it.writeText("data2") }
        val json = """{"chatId":"abc","fileRefs":["uuid1","uuid2"]}"""
        whenever(chatsDao.getById("abc")).thenReturn(DuckAiBridgeChatEntity("abc", json))

        store.deleteChat("abc")

        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }

    @Test
    fun `deleteChat deletes file and removes metadata for valid fileRef`() = runTest {
        val file = File(filesDir, "uuid1").also { it.writeText("data") }
        val json = """{"chatId":"abc","fileRefs":["uuid1"]}"""
        whenever(chatsDao.getById("abc")).thenReturn(DuckAiBridgeChatEntity("abc", json))

        store.deleteChat("abc")

        assertFalse(file.exists())
        verify(fileMetaDao).delete("uuid1")
    }

    @Test
    fun `deleteChat ignores path traversal fileRef and does not touch metadata`() = runTest {
        val json = """{"chatId":"abc","fileRefs":["../../databases/duck_ai_bridge.db"]}"""
        whenever(chatsDao.getById("abc")).thenReturn(DuckAiBridgeChatEntity("abc", json))

        store.deleteChat("abc")

        verify(fileMetaDao, never()).delete(any())
    }

    @Test
    fun `deleteChat succeeds even when chat has no fileRefs`() = runTest {
        val json = """{"chatId":"abc","title":"No files","model":"gpt-5-mini","lastEdit":"2026-04-01T21:31:54.260Z","pinned":false}"""
        whenever(chatsDao.getById("abc")).thenReturn(DuckAiBridgeChatEntity("abc", json))

        assertTrue(store.deleteChat("abc"))
        verify(chatsDao).delete("abc")
    }

    // --- deleteAllChats ---

    @Test
    fun `deleteAllChats clears all chats and file metadata`() = runTest {
        whenever(fileMetaDao.getAll()).thenReturn(emptyList())

        store.deleteAllChats()

        verify(chatsDao).deleteAll()
        verify(fileMetaDao).deleteAll()
    }

    @Test
    fun `deleteAllChats deletes files from disk`() = runTest {
        val file = File(filesDir, "uuid1").also { it.writeText("data") }
        whenever(fileMetaDao.getAll()).thenReturn(
            listOf(DuckAiBridgeFileMetaEntity("uuid1", "chat1", "file.png", "image/png")),
        )

        store.deleteAllChats()

        assertFalse(file.exists())
        verify(fileMetaDao).deleteAll()
        verify(chatsDao).deleteAll()
    }

    @Test
    fun `deleteAllChats ignores path traversal fileRefs`() = runTest {
        val safeFile = File(filesDir, "uuid1").also { it.writeText("data") }
        whenever(fileMetaDao.getAll()).thenReturn(
            listOf(
                DuckAiBridgeFileMetaEntity("uuid1", "chat1", "file.png", "image/png"),
                DuckAiBridgeFileMetaEntity("../../etc/passwd", "chat2", "bad.txt", "text/plain"),
            ),
        )

        store.deleteAllChats()

        assertFalse(safeFile.exists())
        // traversal file should not be deleted — it's outside filesDir
        verify(chatsDao).deleteAll()
    }
}
