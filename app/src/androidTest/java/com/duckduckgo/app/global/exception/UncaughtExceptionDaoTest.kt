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

package com.duckduckgo.app.global.exception

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.db.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UncaughtExceptionDaoTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: UncaughtExceptionDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.uncaughtExceptionDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenInitialisedThereAre0ExceptionsRecorded() {
        assertEquals(0, dao.count())
    }

    @Test
    fun whenNewExceptionAddedThenCountIsIncreased() {
        dao.add(anUncaughtExceptionEntity())
        assertEquals(1, dao.count())
    }

    @Test
    fun whenSeveralExceptionsAddedAndOneDeletedThenNotAllEntriesDeleted() {
        dao.add(anUncaughtExceptionEntity(id = 1))
        dao.add(anUncaughtExceptionEntity(id = 2))
        dao.add(anUncaughtExceptionEntity(id = 3))
        dao.delete(2)
        assertEquals(2, dao.count())
    }

    @Test
    fun whenExceptionRetrievedFromDatabaseThenAllDetailsRestored() {
        val exception = UncaughtExceptionEntity(id = 1, exceptionSource = UncaughtExceptionSource.GLOBAL, message = "foo")
        dao.add(exception)
        val list = dao.all()
        assertEquals(1, list.size)
        list.first().apply {
            assertEquals(1, id)
            assertEquals(exception.exceptionSource, exceptionSource)
            assertEquals(exception.message, message)
        }
    }

    private fun anUncaughtExceptionEntity(id: Long? = null) =
        UncaughtExceptionEntity(id = id ?: 0, exceptionSource = UncaughtExceptionSource.GLOBAL, message = "foo")
}