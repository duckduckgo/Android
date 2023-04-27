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
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_CREATION
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_LOGIN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.BACKGROUND_SYNC
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncDataResponse
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.engine.SyncOperation.DISCARD
import com.duckduckgo.sync.impl.engine.SyncOperation.EXECUTE
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncState.FAIL
import com.duckduckgo.sync.store.model.SyncState.IN_PROGRESS
import com.duckduckgo.sync.store.model.SyncState.SUCCESS
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

@ContributesBinding(scope = AppScope::class)
class RealSyncEngine @Inject constructor(
    private val syncRepository: SyncRepository,
    private val syncApiClient: SyncApiClient,
    private val syncScheduler: SyncScheduler,
    private val syncStateRepository: SyncStateRepository,
    private val plugins: PluginPoint<SyncablePlugin>,
) : SyncEngine {

    override fun syncNow(trigger: SyncTrigger) {
        Timber.d("Sync: petition to sync now trigger: $trigger")
        when (trigger) {
            BACKGROUND_SYNC -> scheduleSync()
            FEATURE_READ -> performSync()
            ACCOUNT_CREATION -> performSync()
            ACCOUNT_LOGIN -> performSync()
        }
    }

    private fun scheduleSync() {
        when (syncScheduler.scheduleOperation()) {
            DISCARD -> {
                Timber.d("Sync: petition to sync denied, debouncing")
            }

            EXECUTE -> {
                Timber.d("Sync: petition to sync accepted, syncing now")
                performSync()
            }
        }
    }

    private fun performSync() {
        // is this the first ever sync? we need to PATCH all data
        // is this triggered after a login? We need to call GET and then PATCH
        // is this a triggered sync? we need to PATCH all data modified after last sync
        // send changes to api
        // receive api changes
        // store state
        // send changes to observers
        val changes = getListOfChanges()

        val syncAttempt = SyncAttempt(state = IN_PROGRESS, meta = "Manual launch")
        syncStateRepository.store(syncAttempt)

        if (changes.isEmpty()) {
            Timber.d("Sync: no changes to sync, asking for remote changes")
            receiveRemoteChange()
        } else {
            Timber.d("Sync: changes to update $changes")
            Timber.d("Sync: starting to sync")
            sendLocalChanges(changes)
        }
    }

    private fun sendLocalChanges(changes: List<SyncChanges>) {
        when (val result = syncApiClient.patch(changes)) {
            is Error -> {
                Timber.d("Sync: patch failed ${result.reason}")
                syncStateRepository.updateSyncState(FAIL)
            }

            is Success -> {
                Timber.d("Sync: patch success")
                syncStateRepository.updateSyncState(SUCCESS)
                sendChanges(result.data)
            }
        }
    }

    private fun receiveRemoteChange() {
        // calls to GET
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
        }.filterNot { it.isEmpty() }
    }

    private fun getChanges(timestamp: String): List<SyncChanges> {
        Timber.d("Sync: gathering changes from $timestamp")
        return plugins.getPlugins().map {
            it.getChanges(timestamp)
        }.filterNot { it.isEmpty() }
    }

    override fun notifyDataChanged() {
        // should sync happen?
        // if so then call syncNow
        // otherwise do nothing
        Timber.d("Sync: notifyDataChanged")
        performSync()
    }

    private fun sendChanges(data: SyncDataResponse) {
        Timber.d("Sync: sendChanges")
    }
}
