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

import com.duckduckgo.sync.impl.engine.SyncOperation.DISCARD
import com.duckduckgo.sync.impl.engine.SyncOperation.EXECUTE
import com.duckduckgo.sync.store.model.SyncState.FAIL
import com.duckduckgo.sync.store.model.SyncState.IN_PROGRESS
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import javax.inject.Inject

interface SyncScheduler {

    fun scheduleOperation(): SyncOperation

}

class RealSyncScheduler @Inject constructor(private val syncStateRepository: SyncStateRepository): SyncScheduler {
    override fun scheduleOperation(): SyncOperation {
        // for non-immediate sync operations we apply a debounce of 10 minutes.
        // on a rudimentary level, without using coroutines
        // we only allow sync operations if the last sync happened more than 10 minutes ago.
        val lastSync = syncStateRepository.current()
        return if (lastSync != null){
            when (lastSync.state) {
                IN_PROGRESS -> DISCARD
                FAIL -> EXECUTE
                else -> {
                    val syncTime = Instant.parse(lastSync.timestamp)
                    val now = Instant.now()
                    val duration = Duration.between(syncTime, now)
                    if (duration.toMinutes() > DEBOUNCE_PERIOD){
                        EXECUTE
                    } else {
                        DISCARD
                    }
                }
            }
        } else {
            EXECUTE
        }
    }

    companion object {
        const val DEBOUNCE_PERIOD = 10 // minutes
    }
}

enum class SyncOperation {
    DISCARD,
    EXECUTE,
}
