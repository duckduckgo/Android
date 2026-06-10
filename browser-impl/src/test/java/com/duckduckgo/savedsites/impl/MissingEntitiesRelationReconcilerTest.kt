/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.savedsites.impl

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import junit.framework.TestCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MissingEntitiesRelationReconcilerTest {

    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val savedSitesEntitiesDao = db.syncEntitiesDao()
    private val testee = MissingEntitiesRelationReconciler(savedSitesEntitiesDao)

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun whenInvalidEntitiesAreConsecutiveThenResultListKeepsCorrectPositions() = runTest {
        val initialEntities = listOf("A", "Inv1", "Inv2", "B")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("B", "A"))

        TestCase.assertEquals(listOf("B", "A", "Inv1", "Inv2"), list)
    }

    @Test
    fun whenInvalidEntitiesBetweenValidEntitiesThenResultListKeepsCorrectPositions() = runTest {
        val initialEntities = listOf("A", "Inv1", "C", "Inv2", "D")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("D", "C", "A"))

        TestCase.assertEquals(listOf("D", "Inv1", "C", "Inv2", "A"), list)
    }

    @Test
    fun whenInvalidEntitiesBetweenMultipleValidEntitiesThenResultListKeepsCorrectPositions() = runTest {
        val initialEntities = listOf("A", "B", "Inv1", "C", "D", "Inv2", "E", "F")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("A", "C", "E", "F", "B", "D"))

        TestCase.assertEquals(listOf("A", "Inv1", "C", "Inv2", "E", "F", "B", "D"), list)
    }

    @Test
    fun whenInvalidEntitiesAndValidItemRemovedThenResultListKeepsCorrectPositions() = runTest {
        val initialEntities = listOf("A", "B", "Inv1", "C", "D", "Inv2", "E", "F")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("A", "B", "C", "E", "F"))

        TestCase.assertEquals(listOf("A", "B", "Inv1", "C", "Inv2", "E", "F"), list)
    }

    @Test
    fun whenInvalidEntitiesAndValidItemAddedAtTheEndOfListThenResultListKeepsCorrectPositions() = runTest {
        val initialEntities = listOf("A", "B", "Inv1", "C", "D", "Inv2", "E", "F")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("A", "B", "C", "E", "F", "G"))

        TestCase.assertEquals(listOf("A", "B", "Inv1", "C", "Inv2", "E", "F", "G"), list)
    }

    @Test
    fun whenInvalidEntitiesAndValidItemAddedInTheMiddleOfListThenResultListKeepsCorrectPositions() = runTest {
        val initialEntities = listOf("A", "B", "Inv1", "C", "D", "Inv2", "E", "F")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("A", "B", "C", "G", "E", "F"))

        TestCase.assertEquals(listOf("A", "B", "Inv1", "C", "G", "Inv2", "E", "F"), list)
    }

    @Test
    fun whenInvalidEntitiesIsFirstItemThenResultListKeepsCorrectPositions() = runTest {
        val initialEntities = listOf("Inv0", "A", "B", "Inv1", "C", "D", "Inv2", "E", "F")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("A", "B", "C", "G", "E", "F"))

        TestCase.assertEquals(listOf("Inv0", "A", "B", "Inv1", "C", "G", "Inv2", "E", "F"), list)
    }

    @Test
    fun whenInvalidEntitiesIsFirstItemAndItemsReorderedThenResultListKeepsCorrectPositions() = runTest {
        val initialEntities = listOf("Inv0", "A", "B", "Inv1", "C", "D", "Inv2", "E", "F")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("F", "E", "A", "B", "C", "G"))

        TestCase.assertEquals(listOf("Inv0", "F", "Inv1", "Inv2", "E", "A", "B", "C", "G"), list)
    }

    @Test
    fun whenInvalidMultipleEntitiesStartListThenResultListKeepsCorrectPositions() = runTest {
        val initialEntities = listOf("Inv0", "Inv1", "Inv2", "A", "B", "C", "D", "E", "F")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("A", "B", "C", "D", "E", "F"))

        TestCase.assertEquals(listOf("Inv0", "Inv1", "Inv2", "A", "B", "C", "D", "E", "F"), list)
    }

    @Test
    fun whenNoInvalidEntitiesThenReturnSameList() = runTest {
        val initialEntities = listOf("A", "B", "C", "D", "E", "F")

        initialEntities.forEachIndexed { index, entityId ->
            if (!entityId.startsWith("Inv")) {
                savedSitesEntitiesDao.insert(Entity(entityId, "title", "www.example.com", type = BOOKMARK, lastModified = "timestamp"))
            }
        }

        val list = testee.reconcileRelations(initialEntities, listOf("A", "B", "C", "G", "E", "F"))

        TestCase.assertEquals(listOf("A", "B", "C", "G", "E", "F"), list)
    }
}
