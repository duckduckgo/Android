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
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_CREATION
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_LOGIN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.APP_OPEN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.BACKGROUND_SYNC
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.DATA_CHANGE
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.api.engine.SyncableDataPersister
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.LOCAL_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.REMOTE_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import com.duckduckgo.sync.api.engine.SyncableDataProvider
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.engine.SyncOperation.DISCARD
import com.duckduckgo.sync.impl.engine.SyncOperation.EXECUTE
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncAttemptState.FAIL
import com.duckduckgo.sync.store.model.SyncAttemptState.IN_PROGRESS
import com.duckduckgo.sync.store.model.SyncAttemptState.SUCCESS
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import timber.log.Timber

@ContributesBinding(scope = AppScope::class)
class RealSyncEngine @Inject constructor(
    private val syncApiClient: SyncApiClient,
    private val syncScheduler: SyncScheduler,
    private val syncStateRepository: SyncStateRepository,
    private val providerPlugins: PluginPoint<SyncableDataProvider>,
    private val persisterPlugins: PluginPoint<SyncableDataPersister>,
) : SyncEngine {

    override fun triggerSync(trigger: SyncTrigger) {
        Timber.d("Sync-Feature: petition to sync now trigger: $trigger")
        when (trigger) {
            BACKGROUND_SYNC -> scheduleSync(trigger)
            APP_OPEN -> performSync(trigger)
            FEATURE_READ -> performSync(trigger)
            DATA_CHANGE -> performSync(trigger)
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
        syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = "Account Creation"))
        getChanges().forEach {
            if (it.isEmpty()) {
                Timber.d("Sync-Feature: ${it.type} local data empty, nothing to send")
                syncStateRepository.updateSyncState(SUCCESS)
                return
            }

            Timber.d("Sync-Feature: sending ${it.type} local data $it")
            patchLocalChanges(it, REMOTE_WINS)
        }
    }

    private fun mergeRemoteData() {
        Timber.d("Sync-Feature: fetching remote data")
        syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = "Account Login"))

        // get remote changes and deduplicate them locally
        getChanges().forEach {
            getRemoteChanges(it, DEDUPLICATION)
        }

        // there might be changes locally that need to be patched
        getChanges().forEach {
            if (it.isEmpty()) {
                Timber.d("Sync-Feature: ${it.type} local data empty, nothing to send")
                syncStateRepository.updateSyncState(SUCCESS)
                return
            } else {
                Timber.d("Sync-Feature: ${it.type}  sending local data $it")
                patchLocalChanges(it, LOCAL_WINS)
            }
        }
    }

    private fun performSync(trigger: SyncTrigger) {
        if (syncInProgress()) {
            Timber.d("Sync-Feature: sync already in progress, throttling")
        } else {
            Timber.d("Sync-Feature: starting to sync")
            syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = trigger.toString()))

            // TODO: Is this good enough? Should we make the calls in parallel?
            getChanges().forEach {
                if (it.isEmpty()) {
                    Timber.d("Sync-Feature: no changes to sync for $it, asking for remote changes")
                    getRemoteChanges(it, TIMESTAMP)
                } else {
                    Timber.d("Sync-Feature: $it changes to update $it")
                    patchLocalChanges(it, TIMESTAMP)
                }
            }
        }
    }

    private fun syncInProgress(): Boolean {
        val currentSync = syncStateRepository.current()
        return if (currentSync != null) {
            if (currentSync.state == IN_PROGRESS) {
                val syncTimestamp = OffsetDateTime.parse(currentSync.timestamp)
                val now = OffsetDateTime.now()
                Duration.between(syncTimestamp, now).toMinutes() > 10
            } else {
                false
            }
        } else {
            false
        }
    }

    private fun patchLocalChanges(
        changes: SyncChangesRequest,
        conflictResolution: SyncConflictResolution,
    ) {
        return when (val result = syncApiClient.patch(changes)) {
            is Error -> {
                Timber.d("Sync-Feature: patch failed ${result.reason}")
                syncStateRepository.updateSyncState(FAIL)
            }

            is Success -> {
                Timber.d("Sync-Feature: patch success")
                persistChanges(result.data, conflictResolution)
                syncStateRepository.updateSyncState(SUCCESS)
            }
        }
    }

    private fun getRemoteChanges(
        changes: SyncChangesRequest,
        conflictResolution: SyncConflictResolution,
    ) {
        Timber.d("Sync-Feature: receive remote change $changes")
        when (val result = syncApiClient.get(changes.type, changes.modifiedSince)) {
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

    private fun getChanges(): List<SyncChangesRequest> {
        return providerPlugins.getPlugins().map {
            it.getChanges()
        }
    }

    private fun persistChanges(
        remoteChanges: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ) {
        persisterPlugins.getPlugins().map {
            it.persist(remoteChanges, conflictResolution)
        }
    }

    override fun onSyncDisabled() {
        syncStateRepository.clearAll()
        persisterPlugins.getPlugins().map {
            it.onSyncDisabled()
        }
    }
}
