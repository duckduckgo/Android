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

package com.duckduckgo.sync.impl.engine

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.sync.store.SyncDatabase
import com.duckduckgo.sync.store.dao.SyncAttemptDao
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncAttemptState
import com.duckduckgo.sync.store.model.SyncAttemptState.SUCCESS
import junit.framework.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncStateRepositoryTest {

    lateinit var repository: SyncStateRepository
    lateinit var syncAttemptDao: SyncAttemptDao

    private lateinit var db: SyncDatabase

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncAttemptDao = db.syncAttemptsDao()

        repository = AppSyncStateRepository(syncAttemptDao)
    }

    @Test
    fun whenSyncDaoIsEmptyCurrentReturnsNull() {
        val currentSync = repository.current()
        assertTrue(currentSync == null)
    }

    @Test
    fun whenSyncInProgressThenCurrentReturnsAttempt() {
        val sync = SyncAttempt(state = SyncAttemptState.IN_PROGRESS)
        repository.store(sync)

        val current = repository.current()
        assertEquals(sync.timestamp, current!!.timestamp)
    }

    @Test
    fun whenSyncStateIsUpdatedThenDaoIsUpdated() {
        val syncInProgress = SyncAttempt(state = SyncAttemptState.IN_PROGRESS)
        repository.store(syncInProgress)

        repository.updateSyncState(SUCCESS)
        val lastSync = repository.current()

        assertEquals(syncInProgress.timestamp, lastSync!!.timestamp)
        assertEquals(SUCCESS, lastSync.state)
    }
}
