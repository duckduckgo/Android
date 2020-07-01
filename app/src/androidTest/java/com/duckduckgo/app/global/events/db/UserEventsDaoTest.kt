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
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class UserEventsDaoTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var db: AppDatabase

    private lateinit var dao: UserEventsDao

    private lateinit var testee: AppUserEventsStore

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        dao = db.userEventsDao()
        testee = AppUserEventsStore(dao, coroutineRule.testDispatcherProvider)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenGetUserEventAndDatabaseEmptyThenReturnNull() = coroutineRule.runBlocking {
        val value = testee.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)
        assertNull(value)
    }

    @Test
    fun whenInsertingUserEventThenReturnSameTimestamp() = coroutineRule.runBlocking {
        val entity = UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)
        testee.registerUserEvent(entity)

        assertEquals(entity.timestamp, testee.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)?.timestamp)
    }

    @Test
    fun whenInsertingSameUserEventThenReplaceOldTimestampWithTheNew() = coroutineRule.runBlocking {
        val entity = UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)
        val newEntity = UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED, System.currentTimeMillis() + - TimeUnit.DAYS.toMillis(1))

        testee.registerUserEvent(entity)
        testee.registerUserEvent(newEntity)

        assertEquals(newEntity.timestamp, testee.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)?.timestamp)
    }
}
