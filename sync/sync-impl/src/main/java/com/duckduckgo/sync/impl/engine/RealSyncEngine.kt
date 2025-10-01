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
import com.duckduckgo.sync.api.engine.FeatureSyncError.COLLECTION_LIMIT_REACHED
import com.duckduckgo.sync.api.engine.FeatureSyncError.INVALID_REQUEST
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
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.engine.SyncOperation.DISCARD
import com.duckduckgo.sync.impl.engine.SyncOperation.EXECUTE
import com.duckduckgo.sync.impl.error.SyncOperationErrorRecorder
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.SyncStore
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncAttemptState.IN_PROGRESS
import com.duckduckgo.sync.store.model.SyncAttemptState.SUCCESS
import com.duckduckgo.sync.store.model.SyncOperationErrorType.DATA_PERSISTER_ERROR
import com.duckduckgo.sync.store.model.SyncOperationErrorType.DATA_PROVIDER_ERROR
import com.duckduckgo.sync.store.model.SyncOperationErrorType.ORPHANS_PRESENT
import com.duckduckgo.sync.store.model.SyncOperationErrorType.TIMESTAMP_CONFLICT
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.INFO
import logcat.logcat
import java.time.Duration
import java.time.OffsetDateTime
import javax.inject.Inject

@ContributesBinding(scope = AppScope::class)
class RealSyncEngine @Inject constructor(
    private val syncApiClient: SyncApiClient,
    private val syncScheduler: SyncScheduler,
    private val syncStateRepository: SyncStateRepository,
    private val syncPixels: SyncPixels,
    private val syncStore: SyncStore,
    private val syncOperationErrorRecorder: SyncOperationErrorRecorder,
    private val providerPlugins: PluginPoint<SyncableDataProvider>,
    private val persisterPlugins: PluginPoint<SyncableDataPersister>,
    private val lifecyclePlugins: PluginPoint<SyncEngineLifecycle>,
) : SyncEngine {

    override fun triggerSync(trigger: SyncTrigger) {
        logcat(INFO) { "Sync-Engine: petition to sync now trigger: $trigger" }
        if (syncStore.isSignedIn() && syncStore.syncingDataEnabled) {
            logcat { "Sync-Engine: sync enabled, triggering operation: $trigger" }
            when (trigger) {
                BACKGROUND_SYNC -> scheduleSync(trigger)
                APP_OPEN -> performSync(trigger)
                FEATURE_READ -> performSync(trigger)
                DATA_CHANGE -> performSync(trigger)
                ACCOUNT_CREATION -> {
                    onSyncEnabled()
                    sendLocalData()
                }
                ACCOUNT_LOGIN -> {
                    onSyncEnabled()
                    performSync(trigger)
                }
            }
        } else {
            logcat { "Sync-Engine: sync disabled, nothing to do" }
        }
    }

    private fun scheduleSync(trigger: SyncTrigger) {
        when (syncScheduler.scheduleOperation()) {
            DISCARD -> {
                logcat { "Sync-Engine: petition to sync debounced" }
            }

            EXECUTE -> {
                logcat { "Sync-Engine: petition to sync accepted, syncing now" }
                performSync(trigger)
            }
        }
    }

    private fun sendLocalData() {
        logcat { "Sync-Engine: initiating first sync" }
        syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = "Account Creation"))
        getChanges().forEach {
            if (it.isEmpty()) {
                logcat { "Sync-Engine: ${it.type} local data empty, nothing to send" }
                syncStateRepository.updateSyncState(SUCCESS)
                return@forEach
            }

            logcat { "Sync-Engine: sending ${it.type} local data $it" }
            patchLocalChanges(it, REMOTE_WINS)
        }
        syncStateRepository.updateSyncState(SUCCESS)
    }

    private fun performSync(trigger: SyncTrigger) {
        if (syncInProgress()) {
            logcat { "Sync-Engine: sync already in progress, throttling" }
        } else {
            logcat { "Sync-Engine: sync is not in progress, starting to sync" }
            syncStateRepository.store(SyncAttempt(state = IN_PROGRESS, meta = trigger.toString()))

            syncPixels.fireDailySuccessRatePixel()
            syncPixels.fireDailyPixel()

            logcat(INFO) { "Sync-Engine: getChanges - performSync" }
            val changes = getChanges()
            performFirstSync(changes.filter { it.isFirstSync() })
            performRegularSync(changes.filter { !it.isFirstSync() })

            logcat { "Sync-Engine: Sync finished" }
            syncStateRepository.updateSyncState(SUCCESS)
        }
    }

    private fun performRegularSync(regularSyncChanges: List<SyncChangesRequest>) {
        regularSyncChanges.forEach { changes ->
            if (changes.isEmpty()) {
                logcat(INFO) { "Sync-Engine: no changes to sync for $changes, asking for remote changes" }
                getRemoteChanges(changes, TIMESTAMP)
            } else {
                logcat(INFO) { "Sync-Engine: $changes changes to update $changes" }
                patchLocalChanges(changes, TIMESTAMP)
            }
        }
    }

    private fun performFirstSync(firstSyncChanges: List<SyncChangesRequest>) {
        val types = firstSyncChanges.map { it.type }

        firstSyncChanges.forEach { changes ->
            logcat(INFO) { "Sync-Engine: first sync for ${changes.type}, asking for remote changes" }
            getRemoteChanges(changes, DEDUPLICATION)
        }

        // give a chance to send changes after dedup
        getChanges().filter { it.type in types }.forEach { changes ->
            if (changes.isEmpty()) {
                logcat { "Sync-Engine: no changes to sync for $changes" }
            } else {
                logcat { "Sync-Engine: $changes changes to update $changes" }
                patchLocalChanges(changes, LOCAL_WINS)
            }
        }
    }

    private fun syncInProgress(): Boolean {
        val currentSync = syncStateRepository.current()
        return if (currentSync != null) {
            logcat { "Sync-Engine: current sync $currentSync" }
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
                val featureError = result.featureError() ?: return
                persisterPlugins.getPlugins().forEach {
                    it.onError(SyncErrorResponse(changes.type, featureError))
                }
                return
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
            }

            is Success -> {
                persistChanges(result.data, conflictResolution)
            }
        }
    }

    private fun getChanges(): List<SyncChangesRequest> {
        return providerPlugins.getPlugins().mapNotNull {
            logcat { "Sync-Engine: asking for changes in ${it.javaClass}" }
            kotlin.runCatching {
                it.getChanges()
            }.getOrElse { error ->
                syncOperationErrorRecorder.record(it.getType().field, DATA_PROVIDER_ERROR)
                null
            }
        }
    }

    private fun persistChanges(
        remoteChanges: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ) {
        persisterPlugins.getPlugins().map {
            kotlin.runCatching {
                when (val result = it.onSuccess(remoteChanges, conflictResolution)) {
                    is SyncMergeResult.Success -> {
                        if (result.orphans) {
                            logcat { "Sync - Orphans present in this sync operation for feature ${remoteChanges.type.field}" }
                            syncOperationErrorRecorder.record(remoteChanges.type.field, ORPHANS_PRESENT)
                        }
                        if (result.timestampConflict) {
                            logcat { "Sync - Timestamp conflict present in this sync operation for feature ${remoteChanges.type.field}" }
                            syncOperationErrorRecorder.record(remoteChanges.type.field, TIMESTAMP_CONFLICT)
                        }
                    }
                    is SyncMergeResult.Error -> {
                        logcat { "Sync - Error while persisting data $result" }
                    }
                }
            }.getOrElse { error ->
                syncOperationErrorRecorder.record(remoteChanges.type.field, DATA_PERSISTER_ERROR)
            }
        }
    }

    private fun onSyncEnabled() {
        syncStateRepository.clearAll()
        persisterPlugins.getPlugins().map {
            it.onSyncEnabled()
        }
        lifecyclePlugins.getPlugins().forEach {
            it.onSyncEnabled()
        }
    }

    override fun onSyncDisabled() {
        syncStateRepository.clearAll()
        persisterPlugins.getPlugins().map {
            it.onSyncDisabled()
        }
        lifecyclePlugins.getPlugins().forEach {
            it.onSyncDisabled()
        }
    }

    private fun Error.featureError(): FeatureSyncError? {
        return when (code) {
            API_CODE.COUNT_LIMIT.code -> COLLECTION_LIMIT_REACHED
            API_CODE.CONTENT_TOO_LARGE.code -> COLLECTION_LIMIT_REACHED
            API_CODE.VALIDATION_ERROR.code -> INVALID_REQUEST
            else -> null
        }
    }
}
