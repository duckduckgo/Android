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

package com.duckduckgo.sync.store.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.sync.store.SyncDatabase
import com.duckduckgo.sync.store.model.SyncApiError
import com.duckduckgo.sync.store.model.SyncApiErrorType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncApiErrorDaoTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SyncDatabase
    private lateinit var dao: SyncApiErrorDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncApiErrorsDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenApiErrorAddedThenItCanBeRetrieved() = runTest {
        val feature = "bookmarks"
        val errorType = SyncApiErrorType.OBJECT_LIMIT_EXCEEDED
        val date = DatabaseDateFormatter.getUtcIsoLocalDate()

        val insert = SyncApiError(feature = feature, errorType = errorType, count = 1, date = date)
        dao.insert(insert)

        val error = dao.featureErrorByDate(feature, errorType.name, date)
        Assert.assertTrue(error!!.count == 1)
    }

    @Test
    fun whenApiErrorIncrementedThenCounterIncremented() = runTest {
        val feature = "bookmarks"
        val errorType = SyncApiErrorType.OBJECT_LIMIT_EXCEEDED
        val date = DatabaseDateFormatter.getUtcIsoLocalDate()

        val insert = SyncApiError(feature = feature, errorType = errorType, count = 1, date = date)
        dao.insert(insert)

        val error = dao.featureErrorByDate(feature, errorType.name, date)
        Assert.assertTrue(error!!.count == 1)

        dao.incrementCount(feature, errorType.name, date)

        val errorIncremented = dao.featureErrorByDate(feature, errorType.name, date)
        Assert.assertTrue(errorIncremented!!.count == 2)
    }
}
