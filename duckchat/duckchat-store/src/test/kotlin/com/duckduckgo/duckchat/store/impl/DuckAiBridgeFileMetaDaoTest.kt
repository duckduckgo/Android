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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeDatabase
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuckAiBridgeFileMetaDaoTest {

    private lateinit var db: DuckAiBridgeDatabase
    private lateinit var dao: DuckAiBridgeFileMetaDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            DuckAiBridgeDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.fileMetaDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `getByChatId returns only rows for the given chatId`() {
        dao.upsert(entity(uuid = "uuid-1", chatId = "chat-1"))
        dao.upsert(entity(uuid = "uuid-2", chatId = "chat-1"))
        dao.upsert(entity(uuid = "uuid-3", chatId = "chat-2"))

        val result = dao.getByChatId("chat-1")

        assertEquals(2, result.size)
        assertTrue(result.all { it.chatId == "chat-1" })
        assertTrue(result.map { it.uuid }.containsAll(listOf("uuid-1", "uuid-2")))
    }

    @Test
    fun `getByChatId returns empty list when no rows match`() {
        dao.upsert(entity(uuid = "uuid-1", chatId = "chat-1"))

        val result = dao.getByChatId("chat-99")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteByChatId removes only rows for the given chatId`() {
        dao.upsert(entity(uuid = "uuid-1", chatId = "chat-1"))
        dao.upsert(entity(uuid = "uuid-2", chatId = "chat-1"))
        dao.upsert(entity(uuid = "uuid-3", chatId = "chat-2"))

        dao.deleteByChatId("chat-1")

        val remaining = dao.getAll()
        assertEquals(1, remaining.size)
        assertEquals("uuid-3", remaining[0].uuid)
    }

    @Test
    fun `deleteByChatId with no matching rows leaves table unchanged`() {
        dao.upsert(entity(uuid = "uuid-1", chatId = "chat-1"))

        dao.deleteByChatId("chat-99")

        assertEquals(1, dao.getAll().size)
    }

    private fun entity(uuid: String, chatId: String) =
        DuckAiBridgeFileMetaEntity(uuid = uuid, chatId = chatId, fileName = "file.jpg", mimeType = "image/jpeg")
}
