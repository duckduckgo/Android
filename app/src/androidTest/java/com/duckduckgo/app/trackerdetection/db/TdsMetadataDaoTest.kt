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

package com.duckduckgo.app.trackerdetection.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.model.TdsMetadata
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TdsMetadataDaoTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var tdsMetadataDao: TdsMetadataDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tdsMetadataDao = db.tdsDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenTdsIsInsertedThenReturnCorrectEtag() {
        val tds = TdsMetadata(eTag = "exampleEtag")
        tdsMetadataDao.insert(tds)

        val etag = tdsMetadataDao.eTag()
        assertEquals("exampleEtag", etag)
    }

    @Test
    fun whenTdsDownloadSuccessfulThenReplaceOldEtag() {
        val tds = TdsMetadata(eTag = "exampleEtag")
        tdsMetadataDao.insert(tds)

        val newTds = TdsMetadata(eTag = "newEtag")
        tdsMetadataDao.tdsDownloadSuccessful(newTds)

        val etag = tdsMetadataDao.eTag()
        assertEquals("newEtag", etag)
    }
}
