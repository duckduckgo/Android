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

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_CREATION
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_LOGIN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.APP_OPEN
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.BACKGROUND_SYNC
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.DATA_CHANGE
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.LOCAL_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.REMOTE_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.engine.SyncOperation.DISCARD
import com.duckduckgo.sync.impl.engine.SyncOperation.EXECUTE
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.SyncStore
import com.duckduckgo.sync.store.model.SyncAttempt
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
    private val syncPixels: SyncPixels,
    private val syncStore: SyncStore,
    private val providerPlugins: PluginPoint<SyncableDataProvider>,
    private val persisterPlugins: PluginPoint<SyncableDataPersister>,
) : SyncEngine {

    override fun triggerSync(trigger: SyncTrigger) {
        Timber.i("Sync-Engine: petition to sync now trigger: $trigger")
        if (syncStore.isSignedIn()) {
            Timber.d("Sync-Engine: sync enabled, triggering operation: $trigger")
            when (trigger) {
                BACKGROUND_SYNC -> scheduleSync(trigger)
                APP_OPEN -> performSync(trigger)
                FEATURE_READ -> performSync(trigger)
                DATA_CHANGE -> performSync(trigger)
                ACCOUNT_CREATION -> sendLocalData()
                ACCOUNT_LOGIN -> performSync(trigger)
            }
        } else {
            Timber.d("Sync-Engine: sync disabled, nothing to do")
        }
    }

    private fun scheduleSync(trigger: SyncTrigger) {
        when (syncScheduler.scheduleOperation()) {
            DISCARD -> {
                Timber.d("Sync-Engine: petition to sync debounced")
            }

            EXECUTE -> {
                Timber.d("Sync-Engine: petition to sync accepted, syncing now")
                performSync(trigger)
            }
        }
    }

    private fun sendLocalData() {
        Timber.d("Sync-Engine: initiating first sync")
        syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = "Account Creation"))
        getChanges().forEach {
            if (it.isEmpty()) {
                Timber.d("Sync-Engine: ${it.type} local data empty, nothing to send")
                syncStateRepository.updateSyncState(SUCCESS)
                return@forEach
            }

            Timber.d("Sync-Engine: sending ${it.type} local data $it")
            patchLocalChanges(it, REMOTE_WINS)
        }
        syncStateRepository.updateSyncState(SUCCESS)
    }

    private fun performSync(trigger: SyncTrigger) {
        if (syncInProgress()) {
            Timber.d("Sync-Engine: sync already in progress, throttling")
        } else {
            Timber.d("Sync-Engine: sync is not in progress, starting to sync")
            syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = trigger.toString()))

            Timber.i("Sync-Engine: getChanges - performSync")
            val changes = getChanges()
            performFirstSync(changes.filter { it.isFirstSync() })
            performRegularSync(changes.filter { !it.isFirstSync() })

            Timber.d("Sync-Engine: Sync finished")
            syncStateRepository.updateSyncState(SUCCESS)
        }
    }

    private fun performRegularSync(regularSyncChanges: List<SyncChangesRequest>) {
        regularSyncChanges.forEach { changes ->
            if (changes.isEmpty()) {
                Timber.i("Sync-Engine: no changes to sync for $changes, asking for remote changes")
                getRemoteChanges(changes, TIMESTAMP)
            } else {
                Timber.i("Sync-Engine: $changes changes to update $changes")
                patchLocalChanges(changes, TIMESTAMP)
            }
        }
    }

    private fun performFirstSync(firstSyncChanges: List<SyncChangesRequest>) {
        val types = firstSyncChanges.map { it.type }

        firstSyncChanges.forEach { changes ->
            Timber.i("Sync-Engine: first sync for ${changes.type}, asking for remote changes")
            getRemoteChanges(changes, DEDUPLICATION)
        }

        // give a chance to send changes after dedup
        getChanges().filter { it.type in types }.forEach { changes ->
            if (changes.isEmpty()) {
                Timber.d("Sync-Engine: no changes to sync for $changes")
            } else {
                Timber.d("Sync-Engine: $changes changes to update $changes")
                patchLocalChanges(changes, LOCAL_WINS)
            }
        }
    }

    private fun syncInProgress(): Boolean {
        val currentSync = syncStateRepository.current()
        return if (currentSync != null) {
            Timber.d("Sync-Engine: current sync $currentSync")
            if (currentSync.state == IN_PROGRESS) {
                val syncTimestamp = OffsetDateTime.parse(currentSync.timestamp)
                val now = OffsetDateTime.now()
                Duration.between(syncTimestamp, now).toMinutes() < 10
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
                syncPixels.fireSyncAttemptErrorPixel(changes.type.toString(), result)
            }

            is Success -> {
                persistChanges(result.data, conflictResolution)
            }
        }
    }

    private fun getRemoteChanges(
        changes: SyncChangesRequest,
        conflictResolution: SyncConflictResolution,
    ) {
        when (val result = syncApiClient.get(changes.type, changes.modifiedSince.value)) {
            is Error -> {
                syncPixels.fireSyncAttemptErrorPixel(changes.type.toString(), result)
            }

            is Success -> {
                persistChanges(result.data, conflictResolution)
            }
        }
    }

    private fun getChanges(): List<SyncChangesRequest> {
        return providerPlugins.getPlugins().map {
            Timber.d("Sync-Engine: asking for changes in ${it.javaClass}")
            it.getChanges()
        }
    }

    private fun persistChanges(
        remoteChanges: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ) {
        persisterPlugins.getPlugins().map {
            when (val result = it.persist(remoteChanges, conflictResolution)) {
                is SyncMergeResult.Success -> {
                    if (result.orphans) {
                        syncPixels.fireOrphanPresentPixel(remoteChanges.type.toString())
                    }
                }
                is SyncMergeResult.Error -> {
                    syncPixels.firePersisterErrorPixel(remoteChanges.type.toString(), result)
                }
            }
        }
    }

    override fun onSyncDisabled() {
        syncStateRepository.clearAll()
        persisterPlugins.getPlugins().map {
            it.onSyncDisabled()
        }
    }
}
