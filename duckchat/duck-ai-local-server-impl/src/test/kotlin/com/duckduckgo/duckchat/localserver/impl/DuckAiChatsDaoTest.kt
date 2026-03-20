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

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiChatEntity
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiChatsDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuckAiChatsDaoTest {

    @Database(entities = [DuckAiChatEntity::class], version = 1, exportSchema = false)
    abstract class TestDatabase : RoomDatabase() {
        abstract fun chatsDao(): DuckAiChatsDao
    }

    private lateinit var db: TestDatabase
    private lateinit var dao: DuckAiChatsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.chatsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert and getAll returns inserted chat`() {
        dao.upsert(DuckAiChatEntity("chat-1", """{"chatId":"chat-1","title":"Hello"}"""))
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("chat-1", all[0].chatId)
    }

    @Test
    fun `upsert twice replaces existing chat`() {
        dao.upsert(DuckAiChatEntity("chat-1", """{"title":"v1"}"""))
        dao.upsert(DuckAiChatEntity("chat-1", """{"title":"v2"}"""))
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("""{"title":"v2"}""", all[0].data)
    }

    @Test
    fun `delete removes specific chat`() {
        dao.upsert(DuckAiChatEntity("chat-1", "{}"))
        dao.upsert(DuckAiChatEntity("chat-2", "{}"))
        dao.delete("chat-1")
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("chat-2", all[0].chatId)
    }

    @Test
    fun `deleteAll removes all chats`() {
        dao.upsert(DuckAiChatEntity("chat-1", "{}"))
        dao.upsert(DuckAiChatEntity("chat-2", "{}"))
        dao.deleteAll()
        assertEquals(0, dao.getAll().size)
    }

    @Test
    fun `getAll returns empty list when no chats`() {
        assertEquals(0, dao.getAll().size)
    }
}
