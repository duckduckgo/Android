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
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.APP_OPEN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.BACKGROUND_SYNC
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution.LOCAL_WINS
import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution.REMOTE_WINS
import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution.TIMESTAMP
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
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
    private val syncApiClient: SyncApiClient,
    private val syncScheduler: SyncScheduler,
    private val syncStateRepository: SyncStateRepository,
    private val plugins: PluginPoint<SyncablePlugin>,
) : SyncEngine {

    override fun syncNow(trigger: SyncTrigger) {
        Timber.d("Sync-Feature: petition to sync now trigger: $trigger")
        when (trigger) {
            BACKGROUND_SYNC -> scheduleSync(trigger)
            APP_OPEN -> performSync(trigger)
            FEATURE_READ -> performSync(trigger)
            ACCOUNT_CREATION -> sendLocalData()
            ACCOUNT_LOGIN -> mergeRemoteData()
        }
    }

    private fun scheduleSync(trigger: SyncTrigger) {
        when (syncScheduler.scheduleOperation()) {
            DISCARD -> {
                Timber.d("Sync-Feature: petition to sync debounced")
            }

            EXECUTE -> {
                Timber.d("Sync-Feature: petition to sync accepted, syncing now")
                performSync(trigger)
            }
        }
    }

    private fun sendLocalData() {
        Timber.d("Sync-Feature: initiating first sync")
        val changes = getChanges()

        if (changes.isEmpty()) {
            Timber.d("Sync-Feature: local data empty, nothing to send")
            return
        }

        Timber.d("Sync-Feature: sending all local data $changes")
        syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = "Account Creation"))
        sendLocalChanges(changes, REMOTE_WINS)
    }

    private fun mergeRemoteData() {
        Timber.d("Sync-Feature: merging remote data")
        syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = "Account Login"))
        getRemoteChange(DEDUPLICATION)

        val changes = getChanges()
        if (changes.isEmpty()) {
            Timber.d("Sync-Feature: local data empty, nothing to send")
            return
        }

        Timber.d("Sync-Feature: sending local data $changes")
        sendLocalChanges(changes, LOCAL_WINS)
    }

    private fun performSync(trigger: SyncTrigger) {
        val changes = getListOfChanges()

        syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = trigger.toString()))

        if (changes.isEmpty()) {
            Timber.d("Sync-Feature: no changes to sync, asking for remote changes")
            receiveRemoteChange(TIMESTAMP)
        } else {
            Timber.d("Sync-Feature: changes to update $changes")
            Timber.d("Sync-Feature: starting to sync")
            sendLocalChanges(changes, TIMESTAMP)
        }
    }

    private fun sendLocalChanges(
        changes: List<SyncChanges>,
        conflictResolution: SyncConflictResolution,
    ) {
        when (val result = syncApiClient.patch(changes)) {
            is Error -> {
                Timber.d("Sync-Feature: patch failed ${result.reason}")
                syncStateRepository.updateSyncState(FAIL)
            }

            is Success -> {
                Timber.d("Sync-Feature: patch success")
                syncStateRepository.updateSyncState(SUCCESS)
                persistChanges(result.data, conflictResolution)
            }
        }
    }

    private fun getRemoteChange(conflictResolution: SyncConflictResolution) {
        val lastCompleted = syncStateRepository.lastCompleted()
        val since = if (lastCompleted == null) {
            Timber.d("Sync-Feature: get all remote changes")
            ""
        } else {
            Timber.d("Sync-Feature: get remote changes since $lastCompleted")
            lastCompleted.timestamp
        }

        when (val result = syncApiClient.get(since)) {
            is Error -> {
                Timber.d("Sync-Feature: get failed ${result.reason}")
                syncStateRepository.updateSyncState(FAIL)
            }

            is Success -> {
                Timber.d("Sync-Feature: get success")
                syncStateRepository.updateSyncState(SUCCESS)
                persistChanges(result.data, conflictResolution)
            }
        }
    }

    private fun getListOfChanges(): List<SyncChanges> {
        val lastAttempt = syncStateRepository.current()
        return if (lastAttempt == null) {
            Timber.d("Sync-Feature: gathering all data")
            getChanges()
        } else {
            Timber.d("Sync-Feature: gathering changes since $lastAttempt")
            getChanges(lastAttempt.timestamp)
        }
    }

    private fun getChanges(timestamp: String = ""): List<SyncChanges> {
        return plugins.getPlugins().map {
            it.getChanges(timestamp)
        }.filterNot { it.isEmpty() }
    }

    override fun notifyDataChanged() {
        Timber.d("Sync-Feature: notifyDataChanged")
        performSync(FEATURE_READ)
    }

    private fun persistChanges(
        remoteChanges: List<SyncChanges>,
        conflictResolution: SyncConflictResolution,
    ) {
        plugins.getPlugins().map {
            it.syncChanges(remoteChanges, conflictResolution)
        }
    }
}
