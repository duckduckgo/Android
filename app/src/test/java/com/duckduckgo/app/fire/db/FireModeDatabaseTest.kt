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

package com.duckduckgo.app.fire.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.tabs.model.TabEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FireModeDatabaseTest {

    private lateinit var database: FireModeDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FireModeDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun whenTabInsertedThenItCanBeReadBack() {
        val tab = TabEntity(tabId = "tab-1", url = "https://duckduckgo.com", title = "DuckDuckGo", position = 0)

        database.tabsDao().insertTab(tab)

        val result = database.tabsDao().tab("tab-1")
        assertNotNull(result)
        assertEquals("tab-1", result!!.tabId)
        assertEquals("https://duckduckgo.com", result.url)
        assertEquals("DuckDuckGo", result.title)
    }
}
