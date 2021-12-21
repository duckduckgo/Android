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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TdsEntityDaoTest {

    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var entityDao: TdsEntityDao

    @Before
    fun before() {
        db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        entityDao = db.tdsEntityDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenListIsUpdatedThenEntitiesAreUpdated() {

        val list =
            listOf(
                TdsEntity("Entity 1 Ltd", "Entity 1", 1.0),
                TdsEntity("Entity 2 Ltd", "Entity 2", 2.0),
                TdsEntity("Entity 3 Ltd", "Entity 3", 3.0))

        entityDao.updateAll(list)

        val updates =
            listOf(
                TdsEntity("Entity 3 LTd", "Entity 3", 4.0),
                TdsEntity("Entity 4 Ltd", "Entity 4", 5.0),
                TdsEntity("Entity 5 LtD", "Entity 5", 6.0))

        entityDao.updateAll(updates)

        val all = entityDao.getAll()

        assertEquals(updates, all)
    }

    @Test
    fun whenEntitiesWithSameNameAddedThenOnlySingleEntryForDomainExists() {

        val list =
            listOf(
                TdsEntity("Entity 1 Ltd", "Entity A", 0.0),
                TdsEntity("Entity 1 Ltd", "Entity B", 0.0),
                TdsEntity("Entity 1 Ltd", "Entity C", 0.0))

        entityDao.updateAll(list)

        assertEquals(1, entityDao.count())
    }

    @Test
    fun whenAllEntitiesDeletedThenGetAllIsEmpty() {

        val list =
            listOf(
                TdsEntity("Entity 1 Ltd", "Entity 1", 1.0),
                TdsEntity("Entity 2 Ltd", "Entity 2", 2.0),
                TdsEntity("Entity 3 Ltd", "Entity 3", 3.0))

        entityDao.updateAll(list)

        assertEquals(3, entityDao.count())

        entityDao.deleteAll()
        assertEquals(0, entityDao.count())
    }

    @Test
    fun whenNetworkEntityAddedThenItIsSavedAndCountMatches() {
        val entity = TdsEntity("Entity 1 Ltd", "Entity 1", 1.0)
        entityDao.insertAll(listOf(entity))
        assertEquals(1, entityDao.count())
    }

    @Test
    fun whenDatabaseIsNewThenItIsEmpty() {
        assertEquals(0, entityDao.count())
    }
}
