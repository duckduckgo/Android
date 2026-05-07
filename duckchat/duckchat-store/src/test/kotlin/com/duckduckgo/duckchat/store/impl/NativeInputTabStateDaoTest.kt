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
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateDao
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeInputTabStateDaoTest {

    private lateinit var db: DuckAiBridgeDatabase
    private lateinit var dao: NativeInputTabStateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            DuckAiBridgeDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.nativeInputTabStateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun whenGetUnknownTabThenReturnsNull() {
        assertNull(dao.getTab("unknown-tab"))
    }

    @Test
    fun whenUpsertThenGetReturnsEntity() {
        val entity = NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3")
        dao.upsert(entity)

        val result = dao.getTab("tab-1")
        assertEquals("claude-3", result?.selectedModelId)
    }

    @Test
    fun whenUpsertTwiceThenLatestValueWins() {
        dao.upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3"))
        dao.upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "gpt-4o"))

        assertEquals("gpt-4o", dao.getTab("tab-1")?.selectedModelId)
    }

    @Test
    fun whenDeleteThenGetReturnsNull() {
        dao.upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3"))
        dao.delete("tab-1")

        assertNull(dao.getTab("tab-1"))
    }

    @Test
    fun whenDeleteUnknownTabThenNoException() {
        dao.delete("never-existed")
    }

    @Test
    fun whenSelectedModelIdIsNullThenPersistedAsNull() {
        dao.upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = null))
        assertNull(dao.getTab("tab-1")?.selectedModelId)
    }
}
