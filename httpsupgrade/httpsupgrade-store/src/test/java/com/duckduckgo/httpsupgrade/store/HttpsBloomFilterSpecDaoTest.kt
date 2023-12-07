/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.httpsupgrade.store

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpsBloomFilterSpecDaoTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: HttpsUpgradeDatabase
    private lateinit var dao: HttpsBloomFilterSpecDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HttpsUpgradeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.httpsBloomFilterSpecDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenModelIsEmptyThenGetIsNull() = runTest {
        assertNull(dao.get())
    }

    @Test
    fun whenModelIsInsertedThenGetIsNotNull() = runTest {
        dao.insert(HttpsBloomFilterSpec(errorRate = 0.1, bitCount = 1000, totalEntries = 55, sha256 = "abc"))
        assertNotNull(dao.get())
    }

    @Test
    fun whenNewModelIsInsertedThenGetIsNotNullAndDetailsUpdates() = runTest {
        dao.insert(HttpsBloomFilterSpec(bitCount = 1000, errorRate = 0.1, totalEntries = 55, sha256 = "abc"))
        dao.insert(HttpsBloomFilterSpec(bitCount = 2000, errorRate = 0.2, totalEntries = 60, sha256 = "xyz"))

        val specification = dao.get()
        assertNotNull(specification)
        assertEquals(2000, specification!!.bitCount)
        assertEquals(0.2, specification.errorRate, 0.01)
        assertEquals(60, specification.totalEntries)
        assertEquals("xyz", specification.sha256)
    }
}
