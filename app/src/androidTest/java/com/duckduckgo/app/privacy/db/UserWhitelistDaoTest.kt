/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.privacy.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.global.db.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserWhitelistDaoTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: UserWhitelistDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.userWhitelistDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenInitializedThenListIsEmpty() {
        assertTrue(dao.all().blockingObserve()!!.isEmpty())
    }

    @Test
    fun whenElementAddedThenListSizeIsOne() {
        dao.insert(DOMAIN)
        assertEquals(1, dao.all().blockingObserve()!!.size)
    }

    @Test
    fun whenElementAddedThenFlowListSizeIsOne() = runBlocking {
        dao.insert(DOMAIN)
        assertEquals(1, dao.allFlow().first().size)
    }

    @Test
    fun whenElementAddedThenContainsIsTrue() {
        dao.insert(DOMAIN)
        assertTrue(dao.contains(DOMAIN))
    }

    @Test
    fun wheElementDeletedThenContainsIsFalse() {
        dao.insert(DOMAIN)
        dao.delete(DOMAIN)
        assertFalse(dao.contains(DOMAIN))
    }

    @Test
    fun whenElementDoesNotExistThenContainsIsFalse() {
        assertFalse(dao.contains(DOMAIN))
    }

    companion object {
        const val DOMAIN = "www.example.com"
    }
}
