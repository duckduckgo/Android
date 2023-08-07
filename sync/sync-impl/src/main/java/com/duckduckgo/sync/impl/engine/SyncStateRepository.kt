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
import com.duckduckgo.sync.store.model.SyncAttemptState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

interface SyncStateRepository {

    fun state(): Flow<SyncAttempt>

    fun store(syncAttempt: SyncAttempt)

    fun current(): SyncAttempt?

    fun updateSyncState(state: SyncAttemptState)

    fun clearAll()

    fun attempts(): List<SyncAttempt>
}

class AppSyncStateRepository @Inject constructor(private val syncAttemptDao: SyncAttemptDao) : SyncStateRepository {
    override fun state(): Flow<SyncAttempt> {
        return syncAttemptDao.attempts().filterNotNull()
    }

    override fun store(syncAttempt: SyncAttempt) {
        syncAttemptDao.insert(syncAttempt)
    }

    override fun current(): SyncAttempt? {
        return syncAttemptDao.lastAttempt()
    }

    override fun updateSyncState(state: SyncAttemptState) {
        val last = syncAttemptDao.lastAttempt()
        if (last != null) {
            val updated = last.copy(state = state)
            syncAttemptDao.insert(updated)
        }
    }

    override fun clearAll() {
        syncAttemptDao.clear()
    }

    override fun attempts(): List<SyncAttempt> {
        return syncAttemptDao.allAttempts()
    }
}
