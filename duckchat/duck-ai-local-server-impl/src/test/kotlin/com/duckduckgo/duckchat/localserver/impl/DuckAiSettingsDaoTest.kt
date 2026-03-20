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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiLocalServerDatabase
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiSettingEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuckAiSettingsDaoTest {

    private lateinit var db: DuckAiLocalServerDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DuckAiLocalServerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert and get setting`() {
        db.settingsDao().upsert(DuckAiSettingEntity(key = "theme", value = "dark"))
        val result = db.settingsDao().get("theme")
        assertEquals("dark", result?.value)
    }

    @Test
    fun `get returns null for missing key`() {
        val result = db.settingsDao().get("nonexistent")
        assertNull(result)
    }

    @Test
    fun `upsert overwrites existing value`() {
        db.settingsDao().upsert(DuckAiSettingEntity(key = "theme", value = "dark"))
        db.settingsDao().upsert(DuckAiSettingEntity(key = "theme", value = "light"))
        val result = db.settingsDao().get("theme")
        assertEquals("light", result?.value)
    }

    @Test
    fun `getAll returns all settings`() {
        db.settingsDao().upsert(DuckAiSettingEntity(key = "a", value = "1"))
        db.settingsDao().upsert(DuckAiSettingEntity(key = "b", value = "2"))
        val all = db.settingsDao().getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun `delete removes setting`() {
        db.settingsDao().upsert(DuckAiSettingEntity(key = "theme", value = "dark"))
        db.settingsDao().delete("theme")
        assertNull(db.settingsDao().get("theme"))
    }
}
