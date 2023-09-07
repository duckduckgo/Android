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

package com.duckduckgo.settings.impl

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.app.global.plugins.*
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.settings.api.*
import com.duckduckgo.settings.impl.SettingsSyncDataPersister.Adapters.Companion.updatesAdapter
import com.duckduckgo.sync.api.*
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableType.SETTINGS
import com.squareup.anvil.annotations.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.*
import javax.inject.*

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataPersister::class)
class SettingsSyncDataPersister @Inject constructor(
    val syncableSettings: PluginPoint<SyncableSetting>,
    val settingsSyncMetadataDao: SettingsSyncMetadataDao,
    val syncSettingsSyncStore: SettingsSyncStore,
    val syncCrypto: SyncCrypto,
    private val dispatchers: DispatcherProvider,
): SyncableDataPersister {
    override fun persist(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution
    ): SyncMergeResult<Boolean> {
        if (changes.type == SETTINGS) {
            val syncableSettings = syncableSettings.getPlugins()
            return runBlocking(dispatchers.io()) {
                Timber.i("Sync-Settings: persist() changes=${changes.jsonString}")
                val result = process(changes, syncableSettings, conflictResolution)
                result
            }
        }
        return Success(false)
    }

    private suspend fun process(changes: SyncChangesResponse, syncableSettings: Collection<SyncableSetting>, conflictResolution: SyncConflictResolution): SyncMergeResult<Boolean> {
        if (changes.jsonString.isEmpty()) {
            Timber.i("Sync-Settings: jsonString is empty")
            //update timestamps
            return Success(false)
        }

        val response = runCatching {
            updatesAdapter.fromJson(changes.jsonString)!!
        }.getOrElse {
            Timber.i("Sync-Settings: process() error parsing credentials ${it.message}")
            return SyncMergeResult.Error(reason = "Error parsing credentials ${it.message}")
        }

        val result = processEntries(response.settings, syncableSettings, conflictResolution)

        if (result is Success) {
            Timber.i("Sync-Settings: updating timestamps post-persist")
            syncSettingsSyncStore.serverModifiedSince = response.settings.last_modified
            syncSettingsSyncStore.clientModifiedSince = syncSettingsSyncStore.startTimeStamp

            if (conflictResolution == SyncConflictResolution.DEDUPLICATION) {
                settingsSyncMetadataDao.getAllObservable().firstOrNull()?.filter { it.modified_at != null }?.forEach {
                    Timber.i("Sync-Settings: post-dedup update timestamp for ${it.key} so we can send them next time")
                    settingsSyncMetadataDao.addOrUpdate(
                        SettingsSyncMetadataEntity(key = it.key, modified_at = SyncDateProvider.now(), deleted_at = null),
                    )
                }
            }
            //prune metadata objects?
            //should prune deleted objects?
            //should nullify timestamps?
        }

        Timber.i("Sync-Settings: process() result=$result")
        return result
    }

    private fun processEntries(settings: CredentialsSyncEntries, syncableSettings: Collection<SyncableSetting>, conflictResolution: SyncConflictResolution): SyncMergeResult<Boolean>  {
        settings.entries.forEach { entry ->
            Timber.i("Sync-Settings: processEntries() entry=${entry.key}")
            val syncableFeature = syncableSettings.firstOrNull { it.key == entry.key } ?: return@forEach
            Timber.i("Sync-Settings: plugin found for ${entry.key}")
            when(conflictResolution) {
                SyncConflictResolution.DEDUPLICATION -> {
                    val valueUpdated = if(entry.isDeleted()) {
                        syncableFeature.mergeRemote(null)
                    } else {
                        val decryptedValue = entry.value.takeUnless { it.isNullOrEmpty() }?.let { syncCrypto.decrypt(it)}
                        syncableFeature.mergeRemote(decryptedValue)
                    }
                    if (valueUpdated) { //TODO: do we need this?
                        settingsSyncMetadataDao.addOrUpdate(
                            SettingsSyncMetadataEntity(
                                key = entry.key,
                                modified_at = "",
                                deleted_at = ""
                            )
                        )
                    }
                }
                SyncConflictResolution.REMOTE_WINS -> applyChanges(syncableFeature, entry)
                SyncConflictResolution.LOCAL_WINS -> applyChanges(syncableFeature, entry)
                SyncConflictResolution.TIMESTAMP -> applyChanges(syncableFeature, entry)
            }
        }

        //TODO: should we do error handling per setting?
        return Success(true)
    }

    private fun applyChanges(syncableFeature: SyncableSetting, entry: SettingEntryResponse): SyncMergeResult<Boolean> {
        val localCredential = settingsSyncMetadataDao.get(entry.key)
        val clientModifiedSinceMillis =
            runCatching { DatabaseDateFormatter.parseIso8601ToMillis(syncSettingsSyncStore.startTimeStamp) }.getOrDefault(0)
        val entitiyModifiedMillis =
            runCatching { DatabaseDateFormatter.parseIso8601ToMillis(localCredential?.modified_at.orEmpty()) }.getOrDefault(0)
        val hasDataChangedWhileSyncing = entitiyModifiedMillis > clientModifiedSinceMillis
        if (hasDataChangedWhileSyncing) return SyncMergeResult.Error(reason = "Data changed while syncing")

        if(entry.isDeleted()) {
            syncableFeature.save(null)
        } else {
            val decryptedValue = entry.value.takeUnless { it.isNullOrEmpty() }?.let { syncCrypto.decrypt(it)}
            syncableFeature.save(decryptedValue)
        }
        return Success(true)
    }

    override fun onSyncDisabled() {
        syncSettingsSyncStore.serverModifiedSince = "0"
        syncSettingsSyncStore.clientModifiedSince = "0"
        syncSettingsSyncStore.startTimeStamp = "0"
        settingsSyncMetadataDao.removeAll()
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory()).build()
            val updatesAdapter: JsonAdapter<CredentialsSyncRemoteUpdates> =
                moshi.adapter(CredentialsSyncRemoteUpdates::class.java)
        }
    }
}

data class CredentialsSyncRemoteUpdates(
    val settings: CredentialsSyncEntries,
)

data class CredentialsSyncEntries(
    val entries: List<SettingEntryResponse>,
    val last_modified: String,
)

data class SettingEntryResponse(
    val key: String,
    val value: String? = null,
    val deleted: String? = null,
    val last_modified: String? = null,
)

internal fun SettingEntryResponse.isDeleted(): Boolean {
    return this.deleted.isNullOrEmpty().not()
}
