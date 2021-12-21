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

package com.duckduckgo.app.global.events.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class UserEventsDaoTest {

    @get:Rule var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var db: AppDatabase

    private lateinit var dao: UserEventsDao

    private lateinit var testee: AppUserEventsStore

    @Before
    fun before() {
        db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java)
                .build()
        dao = db.userEventsDao()
        testee = AppUserEventsStore(dao, coroutineRule.testDispatcherProvider)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenGetUserEventAndDatabaseEmptyThenReturnNull() =
        coroutineRule.runBlocking {
            assertNull(testee.getUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED))
        }

    @Test
    fun whenInsertingUserEventThenTimestampIsNotNull() =
        coroutineRule.runBlocking {
            testee.registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)

            assertNotNull(testee.getUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)?.timestamp)
        }

    @Test
    fun whenInsertingSameUserEventThenReplaceOldTimestamp() =
        coroutineRule.runBlocking {
            testee.registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)
            val timestamp = testee.getUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)?.timestamp

            testee.registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)

            assertNotEquals(
                timestamp, testee.getUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)?.timestamp)
        }
}
