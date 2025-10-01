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

package com.duckduckgo.sync.settings.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.*
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableType.SETTINGS
import com.duckduckgo.sync.settings.api.SyncableSetting
import com.duckduckgo.sync.settings.impl.SettingsSyncDataPersister.Adapters.Companion.updatesAdapter
import com.squareup.anvil.annotations.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.*

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataPersister::class)
class SettingsSyncDataPersister @Inject constructor(
    val syncableSettings: PluginPoint<SyncableSetting>,
    val settingsSyncMetadataDao: SettingsSyncMetadataDao,
    val syncSettingsSyncStore: SettingsSyncStore,
    val syncCrypto: SyncCrypto,
    private val dispatchers: DispatcherProvider,
) : SyncableDataPersister {
    override fun onSyncEnabled() {
        if (isLocalDataDirty()) {
            onSyncDisabled()
        }
    }

    override fun onSuccess(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        if (changes.type == SETTINGS) {
            val syncableSettings = syncableSettings.getPlugins()
            return runBlocking(dispatchers.io()) {
                logcat(INFO) { "Sync-Settings: persist() changes=${changes.jsonString}" }
                val result = process(changes, syncableSettings, conflictResolution)
                result
            }
        }
        return Success()
    }

    override fun onError(error: SyncErrorResponse) {
        // no-op
    }

    private suspend fun process(
        changes: SyncChangesResponse,
        syncableSettings: Collection<SyncableSetting>,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        if (changes.jsonString.isEmpty()) {
            logcat(INFO) { "Sync-Settings: jsonString is empty" }
            return Success()
        }

        val response = runCatching {
            updatesAdapter.fromJson(changes.jsonString)!!
        }.getOrElse {
            logcat(INFO) { "Sync-Settings: process() error parsing settings ${it.message}" }
            return SyncMergeResult.Error(reason = "Error parsing settings ${it.message}")
        }

        val result = processEntries(response.settings, syncableSettings, conflictResolution)

        if (result is Success) {
            logcat(INFO) { "Sync-Settings: updating timestamps post-persist" }
            syncSettingsSyncStore.serverModifiedSince = response.settings.last_modified
            syncSettingsSyncStore.clientModifiedSince = syncSettingsSyncStore.startTimeStamp

            if (conflictResolution == SyncConflictResolution.DEDUPLICATION) {
                // first sync has a special case: ensure we send next time settings not updated during deduplication
                settingsSyncMetadataDao.getAllObservable().firstOrNull()?.filterNot { it.modified_at.isNullOrEmpty() }?.forEach {
                    logcat(INFO) { "Sync-Settings: post-dedup update timestamp for ${it.key} so we can send them next time" }
                    settingsSyncMetadataDao.addOrUpdate(
                        SettingsSyncMetadataEntity(key = it.key, modified_at = SyncDateProvider.now(), deleted_at = null),
                    )
                }
            }
        }

        logcat(INFO) { "Sync-Settings: process() result=$result" }
        return result
    }

    private fun processEntries(
        settings: SettingsSyncEntries,
        syncableSettings: Collection<SyncableSetting>,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        settings.entries.forEach { entry ->
            logcat(INFO) { "Sync-Settings: processEntries() entry=${entry.key}" }
            val syncableFeature = syncableSettings.firstOrNull { it.key == entry.key } ?: return@forEach
            logcat(INFO) { "Sync-Settings: plugin found for ${entry.key}" }
            when (conflictResolution) {
                SyncConflictResolution.DEDUPLICATION -> {
                    val valueUpdated = if (entry.isDeleted()) {
                        syncableFeature.deduplicate(null)
                    } else {
                        val decryptedValue = entry.value.takeUnless { it.isNullOrEmpty() }?.let { syncCrypto.decrypt(it) }
                        syncableFeature.deduplicate(decryptedValue)
                    }
                    if (valueUpdated) {
                        settingsSyncMetadataDao.addOrUpdate(
                            SettingsSyncMetadataEntity(
                                key = entry.key,
                                modified_at = "",
                                deleted_at = "",
                            ),
                        )
                    }
                }
                else -> applyChanges(syncableFeature, entry)
            }
        }

        return Success()
    }

    private fun applyChanges(syncableFeature: SyncableSetting, entry: SettingEntryResponse): SyncMergeResult {
        val localCredential = settingsSyncMetadataDao.get(entry.key)
        val clientModifiedSinceMillis =
            runCatching { DatabaseDateFormatter.parseIso8601ToMillis(syncSettingsSyncStore.startTimeStamp) }.getOrDefault(0)
        val entityModifiedMillis =
            runCatching { DatabaseDateFormatter.parseIso8601ToMillis(localCredential?.modified_at.orEmpty()) }.getOrDefault(0)
        val hasDataChangedWhileSyncing = entityModifiedMillis > clientModifiedSinceMillis
        if (hasDataChangedWhileSyncing) return SyncMergeResult.Error(reason = "Data changed while syncing")

        if (entry.isDeleted()) {
            syncableFeature.save(null)
        } else {
            val decryptedValue = entry.value.takeUnless { it.isNullOrEmpty() }?.let { syncCrypto.decrypt(it) }
            syncableFeature.save(decryptedValue)
        }
        return Success()
    }

    override fun onSyncDisabled() {
        syncSettingsSyncStore.serverModifiedSince = "0"
        syncSettingsSyncStore.clientModifiedSince = "0"
        syncSettingsSyncStore.startTimeStamp = "0"
        settingsSyncMetadataDao.removeAll()
        syncableSettings.getPlugins().forEach { it.onSyncDisabled() }
    }

    private fun isLocalDataDirty(): Boolean {
        return syncSettingsSyncStore.serverModifiedSince != "0"
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory()).build()
            val updatesAdapter: JsonAdapter<SettingsSyncRemoteUpdates> =
                moshi.adapter(SettingsSyncRemoteUpdates::class.java)
        }
    }
}

data class SettingsSyncRemoteUpdates(
    val settings: SettingsSyncEntries,
)

data class SettingsSyncEntries(
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
