/*
 * Copyright (c) 2019 DuckDuckGo
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
import com.duckduckgo.app.trackerdetection.model.TemporaryTrackingWhitelistedDomain
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemporaryTrackingWhitelistDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TemporaryTrackingWhitelistDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        dao = db.temporaryTrackingWhitelistDao()
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
        dao.insertAll(listOf(createEntity(domain)))
        assertEquals(1, dao.count())
    }

    @Test
    fun whenEntityInsertedThenContainsEntity() {
        val entity = createEntity(domain)
        dao.insertAll(listOf(entity))
        assertTrue(dao.getAll().contains(entity))
    }

    @Test
    fun whenSecondUniqueEntityInsertedThenCountIsTwo() {
        dao.insertAll(listOf(createEntity(domain)))
        dao.insertAll(listOf(createEntity(anotherDomain)))
        assertEquals(2, dao.count())
    }

    @Test
    fun whenSecondDuplicateEntityInsertedThenCountIsOne() {
        dao.insertAll(listOf(createEntity(domain)))
        dao.insertAll(listOf(createEntity(domain)))
        assertEquals(1, dao.count())
    }

    @Test
    fun whenAllUpdatedThenPreviousValuesAreReplaced() {
        val initialEntity = createEntity(domain)
        val replacementEntity = createEntity(anotherDomain)

        dao.insertAll(listOf(initialEntity))
        dao.updateAll(listOf(replacementEntity))
        assertEquals(1, dao.count())
        assertTrue(dao.getAll().contains(replacementEntity))
    }

    @Test
    fun whenAllDeletedThenCountIsZero() {
        val entity = createEntity(domain)
        dao.insertAll(listOf(entity))
        dao.deleteAll()
        assertEquals(0, dao.count())
    }

    @Test
    fun whenElementAddedThenContainsIsTrue() {
        val entity = createEntity(domain)
        dao.insertAll(listOf(entity))
        assertTrue(dao.contains(domain))
    }

    @Test
    fun wheElementDeletedThenContainsIsFalse() {
        val entity = createEntity(domain)
        dao.insertAll(listOf(entity))
        dao.deleteAll()
        Assert.assertFalse(dao.contains(domain))
    }

    @Test
    fun whenElementDoesNotExistThenContainsIsFalse() {
        Assert.assertFalse(dao.contains(domain))
    }

    private fun createEntity(domain: String): TemporaryTrackingWhitelistedDomain {
        return TemporaryTrackingWhitelistedDomain(domain)
    }

    companion object {
        var domain = "domain.com"
        var anotherDomain = "anotherdomain.com"
    }
}
