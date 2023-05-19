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

import com.duckduckgo.sync.store.dao.SyncAttemptDao
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface SyncStateRepository {
    fun store(syncAttempt: SyncAttempt)

    fun current(): SyncAttempt?

    fun all(): Flow<List<SyncAttempt>>

    fun updateSyncState(state: SyncState)
}

class AppSyncStateRepository @Inject constructor(private val syncAttemptDao: SyncAttemptDao) : SyncStateRepository {
    override fun store(syncAttempt: SyncAttempt) {
        syncAttemptDao.insert(syncAttempt)
    }

    override fun current(): SyncAttempt? {
        return syncAttemptDao.lastAttempt()
    }

    override fun all(): Flow<List<SyncAttempt>> {
        return syncAttemptDao.attempts()
    }

    override fun updateSyncState(state: SyncState) {
        val last = syncAttemptDao.lastAttempt()
        if (last != null) {
            val updated = last.copy(state = state)
            syncAttemptDao.insert(updated)
        }
    }
}
