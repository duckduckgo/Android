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
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuckAiBridgeSettingsDaoTest {

    private lateinit var db: DuckAiBridgeDatabase
    private lateinit var dao: DuckAiBridgeSettingsDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            DuckAiBridgeDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.settingsDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `observe emits null when the key is absent`() = runTest {
        assertNull(dao.observe("usageLimits").first())
    }

    @Test
    fun `observe emits the row once it is written`() = runTest {
        dao.upsert(DuckAiBridgeSettingEntity(key = "usageLimits", value = "{}"))

        assertEquals(DuckAiBridgeSettingEntity(key = "usageLimits", value = "{}"), dao.observe("usageLimits").first())
    }

    @Test
    fun `observe emits null again once the row is deleted`() = runTest {
        dao.upsert(DuckAiBridgeSettingEntity(key = "usageLimits", value = "{}"))
        dao.delete("usageLimits")

        assertNull(dao.observe("usageLimits").first())
    }

    @Test
    fun `observe returns only the requested key`() = runTest {
        dao.upsert(DuckAiBridgeSettingEntity(key = "other", value = "x"))

        assertNull(dao.observe("usageLimits").first())
    }
}
