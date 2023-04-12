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

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncChanges
import com.duckduckgo.sync.api.SyncEngine
import com.duckduckgo.sync.api.SyncablePlugin
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncState.IN_PROGRESS
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(scope = AppScope::class)
class RealSyncEngine @Inject constructor(
    private val syncRepository: SyncRepository,
    private val syncStateRepository: SyncStateRepository,
    private val plugins: PluginPoint<SyncablePlugin>,
) : SyncEngine {

    override fun syncNow() {
        val changes = getListOfChanges()
        Timber.d("Sync: changes to update $changes")
        Timber.d("Sync: starting to sync")

        val syncAttempt = SyncAttempt(state = IN_PROGRESS, meta = "Manual launch")
        syncStateRepository.store(syncAttempt)
    }

    private fun getListOfChanges(): List<SyncChanges> {
        val lastAttempt = syncStateRepository.current()
        return if (lastAttempt == null) {
            initialSync()
        } else {
            getChanges(lastAttempt.timestamp)
        }

    }

    private fun initialSync(): List<SyncChanges> {
        Timber.d("Sync: initialSync")
        return plugins.getPlugins().map {
            it.getChanges("")
        }
    }

    private fun getChanges(timestamp: String): List<SyncChanges> {
        Timber.d("Sync: gathering changes from $timestamp")
        return plugins.getPlugins().map {
            it.getChanges(timestamp)
        }
    }

    override fun notifyDataChanged() {
        Timber.d("Sync: notifyDataChanged")
        syncNow()
    }
}
