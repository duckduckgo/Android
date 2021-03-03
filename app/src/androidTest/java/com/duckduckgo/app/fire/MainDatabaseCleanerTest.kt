/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.fire

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MainDatabaseCleanerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val mainDatabaseLocator = MainDatabaseLocator(context)

    private lateinit var testee: MainDatabaseCleaner

    @Before
    fun before() {
        testee = MainDatabaseCleaner(db, mainDatabaseLocator)
    }

    @After
    fun after() {
        if (db.isOpen) { db.close() }
    }

    @Test
    fun whenCleanDatabaseFinishedThenAppDatabaseIsClosed() = runBlocking {
        testee.cleanDatabase()

        assertFalse(db.isOpen)
    }

    @Test
    fun whenCleanDatabaseAndDatabaseExistsThenReturnTrue() = runBlocking {
        assertTrue(testee.cleanDatabase())
    }
}
