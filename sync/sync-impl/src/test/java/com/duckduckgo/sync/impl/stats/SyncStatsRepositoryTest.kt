/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.stats

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.sync.impl.engine.AppSyncStateRepository
import com.duckduckgo.sync.impl.engine.SyncStateRepository
import com.duckduckgo.sync.store.SyncDatabase
import com.duckduckgo.sync.store.dao.SyncAttemptDao
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncState.FAIL
import com.duckduckgo.sync.store.model.SyncState.IN_PROGRESS
import com.duckduckgo.sync.store.model.SyncState.SUCCESS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatsRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var syncAttemptDao: SyncAttemptDao

    private lateinit var db: SyncDatabase
    private lateinit var stateRepository: SyncStateRepository
    private lateinit var testee: SyncStatsRepository

    private val today = OffsetDateTime.now(ZoneOffset.UTC)

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncAttemptDao = db.syncAttemptsDao()
        stateRepository = AppSyncStateRepository(syncAttemptDao)

        testee = AppSyncStatsRepository(stateRepository)
    }

    @Test
    fun whenNoSyncAttempsThenStatsForTheDayIsEmpty(){
        val today = OffsetDateTime.now(ZoneOffset.UTC)

        val result = testee.getSyncStats(today)

        assertTrue(result.totalAttempts == 0)
    }

    @Test
    fun whenSyncAttemptsAllSuccessThenStatsForTheDayHasData(){
        val today = OffsetDateTime.now(ZoneOffset.UTC)

        stateRepository.store(SyncAttempt(state = SUCCESS))
        stateRepository.store(SyncAttempt(state = SUCCESS))
        stateRepository.store(SyncAttempt(state = SUCCESS))
        stateRepository.store(SyncAttempt(state = SUCCESS))
        stateRepository.store(SyncAttempt(state = SUCCESS))

        val result = testee.getSyncStats(today)

        assertTrue(result.totalAttempts == 5)
        assertTrue(result.successRate == 1.0f)
        assertTrue(result.failureRate == 0.0f)
    }

    @Test
    fun whenSyncAttemptsAllFailedOrInProgressThenStatsForTheDayHasData(){
        val today = OffsetDateTime.now(ZoneOffset.UTC)

        stateRepository.store(SyncAttempt(state = FAIL))
        stateRepository.store(SyncAttempt(state = FAIL))
        stateRepository.store(SyncAttempt(state = FAIL))
        stateRepository.store(SyncAttempt(state = FAIL))
        stateRepository.store(SyncAttempt(state = IN_PROGRESS))

        val result = testee.getSyncStats(today)

        assertTrue(result.totalAttempts == 5)
        assertTrue(result.successRate == 0.0f)
        assertTrue(result.failureRate == 1.0f)
    }

    @Test
    fun whenSyncAttemptsMixedThenStatsForTheDayHasData(){
        val today = OffsetDateTime.now(ZoneOffset.UTC)

        stateRepository.store(SyncAttempt(state = SUCCESS))
        stateRepository.store(SyncAttempt(state = SUCCESS))
        stateRepository.store(SyncAttempt(state = FAIL))
        stateRepository.store(SyncAttempt(state = FAIL))
        stateRepository.store(SyncAttempt(state = IN_PROGRESS))

        val result = testee.getSyncStats(today)

        assertTrue(result.totalAttempts == 5)
        assertTrue(result.successRate == 0.4f)
        assertTrue(result.failureRate == 0.6f)
    }

}
