/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.entities.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EntityListDaoTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var entityListDao: EntityListDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        entityListDao = db.networkEntityDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenListIsUpdatedThenEntitiesAreUpdated() {

        val list = listOf(
            EntityListEntity("domain", "entity"),
            EntityListEntity("domain2", "entity2"),
            EntityListEntity("domain3", "entity3")
        )

        entityListDao.updateAll(list)

        val updates = listOf(
            EntityListEntity("domain", "entity3"),
            EntityListEntity("domain2", "entity4"),
            EntityListEntity("domain3", "entity5")
        )

        entityListDao.updateAll(updates)

        val all = entityListDao.getAll()

        assertEquals(updates, all)
    }

    @Test
    fun whenEntitiesWithSameDomainAddedThenOnlySingleEntryForDomainExists() {

        val list = listOf(
            EntityListEntity("domain", "entity"),
            EntityListEntity("domain", "entity2"),
            EntityListEntity("domain", "entity3")
        )

        entityListDao.updateAll(list)

        assertEquals(1, entityListDao.count())

    }

    @Test
    fun whenAllEntitiesDeletedThenGetAllIsEmpty() {

        val list = listOf(
            EntityListEntity("domain", "entity"),
            EntityListEntity("domain2", "entity2"),
            EntityListEntity("domain3", "entity3")
        )

        entityListDao.updateAll(list)

        assertEquals(3, entityListDao.count())

        entityListDao.deleteAll()
        assertEquals(0, entityListDao.count())
    }

    @Test
    fun whenNetworkEntityAddedThenItIsSavedAndCountMatches() {
        val entity = EntityListEntity("domain", "entity")
        entityListDao.insertAll(listOf(entity))
        assertEquals(1, entityListDao.count())
    }

    @Test
    fun whenDatabaseIsNewThenItIsEmpty() {
        assertEquals(0, entityListDao.count())
    }

}