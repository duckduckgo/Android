/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.model.TdsCnameEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TdsCnameEntityDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TdsCnameEntityDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        dao = db.tdsCnameEntityDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenModelIsEmptyThenCountIsZero() {
        assertEquals(0, dao.count())
    }

    @Test
    fun whenEntityInsertedThenCountIsOne() {
        dao.insertAll(listOf(TdsCnameEntity("cloaked.com", "uncloaked.com")))
        assertEquals(1, dao.count())
    }

    @Test
    fun whenEntityInsertedThenContainsEntity() {
        val entity = TdsCnameEntity("cloaked.com", "uncloaked.com")
        dao.insertAll(listOf(entity))
        assertTrue(dao.getAll().contains(entity))
    }

    @Test
    fun whenSecondUniqueEntityInsertedThenCountIsTwo() {
        dao.insertAll(listOf(TdsCnameEntity("cloaked.com", "uncloaked.com")))
        dao.insertAll(listOf(TdsCnameEntity("cloaked2.com", "uncloaked.com")))
        assertEquals(2, dao.count())
    }

    @Test
    fun whenSecondDuplicateEntityInsertedThenCountIsOne() {
        dao.insertAll(listOf(TdsCnameEntity("cloaked.com", "uncloaked.com")))
        dao.insertAll(listOf(TdsCnameEntity("cloaked.com", "uncloaked.com")))
        assertEquals(1, dao.count())
    }

    @Test
    fun whenAllUpdatedThenPreviousValuesAreReplaced() {
        val initialHostName = TdsCnameEntity("cloaked.com", "uncloaked.com")
        val replacementHostName = TdsCnameEntity("cloaked.com", "uncloaked.com")

        dao.insertAll(listOf(initialHostName))
        dao.updateAll(listOf(replacementHostName))
        assertEquals(1, dao.count())
        assertTrue(dao.getAll().contains(replacementHostName))
    }

    @Test
    fun whenAllDeletedThenCountIsZero() {
        val hostName = TdsCnameEntity("cloaked.com", "uncloaked.com")
        dao.insertAll(listOf(hostName))
        dao.deleteAll()
        assertEquals(0, dao.count())
    }
}
