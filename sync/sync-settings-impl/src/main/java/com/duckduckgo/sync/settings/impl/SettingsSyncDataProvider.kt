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

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.ModifiedSince
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncableDataProvider
import com.duckduckgo.sync.api.engine.SyncableType
import com.duckduckgo.sync.api.engine.SyncableType.SETTINGS
import com.duckduckgo.sync.settings.api.SyncableSetting
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.*
import logcat.LogPriority.INFO
import logcat.logcat

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataProvider::class)
class SettingsSyncDataProvider @Inject constructor(
    val syncableSettings: PluginPoint<SyncableSetting>,
    val settingsSyncMetadataDao: SettingsSyncMetadataDao,
    val settingsSyncStore: SettingsSyncStore,
    val syncCrypto: SyncCrypto,
) : SyncableDataProvider {

    override fun getType(): SyncableType = SETTINGS
    override fun getChanges(): SyncChangesRequest {
        val syncableSettings = syncableSettings.getPlugins()
        if (settingsSyncStore.serverModifiedSince == "0") {
            val keys = syncableSettings.map { it.key }
            logcat(INFO) { "Sync-Settings: initialize keys=$keys" }
            settingsSyncMetadataDao.initialize(keys)
        }

        val since = settingsSyncStore.clientModifiedSince
        val updates = getUpdatesSince(syncableSettings, since)
        logcat(INFO) { "Sync-Settings: getChanges() since=$since updates=$updates" }
        return formatUpdates(updates)
    }

    private fun getUpdatesSince(
        syncableSettings: Collection<SyncableSetting>,
        clientModifiedSince: String,
    ): List<SettingEntry> {
        settingsSyncStore.startTimeStamp = SyncDateProvider.now()

        val updates = mutableListOf<SettingEntry>()
        if (clientModifiedSince == "0") {
            syncableSettings.forEach { setting ->
                updates.add(
                    setting.asSettingEntry(clientModifiedSince = SyncDateProvider.now()),
                )
            }
        } else {
            val settingsToUpdate = settingsSyncMetadataDao.getChangesSince(clientModifiedSince)
            syncableSettings.forEach { setting ->
                val metadata = settingsToUpdate.find { it.key == setting.key } ?: return@forEach
                logcat(INFO) { "Sync-Settings: changes since=$clientModifiedSince metadata=$metadata" }
                updates.add(
                    setting.asSettingEntry(metadata.modified_at ?: SyncDateProvider.now()),
                )
            }
        }

        return updates
    }

    private fun formatUpdates(updates: List<SettingEntry>): SyncChangesRequest {
        val modifiedSince = if (settingsSyncStore.serverModifiedSince == "0") {
            ModifiedSince.FirstSync
        } else {
            ModifiedSince.Timestamp(settingsSyncStore.serverModifiedSince)
        }

        logcat(INFO) { "Sync-Settings: formatUpdates() modifiedSince=$modifiedSince updates=$updates" }

        return if (updates.isEmpty()) {
            SyncChangesRequest(SETTINGS, "", modifiedSince)
        } else {
            val credentialsUpdates = SyncSettingsUpdates(
                updates = updates,
                modified_since = settingsSyncStore.serverModifiedSince,
            )
            val patch = SyncSettingsRequest(
                settings = credentialsUpdates,
                client_timestamp = SyncDateProvider.now(),
            )
            val allDataJSON = Adapters.patchAdapter.toJson(patch)
            SyncChangesRequest(SETTINGS, allDataJSON, modifiedSince)
        }
    }

    private fun SyncableSetting.asSettingEntry(clientModifiedSince: String): SettingEntry {
        val value = getValue()?.let { syncCrypto.encrypt(it) }
        return SettingEntry(
            key = key,
            value = value ?: "",
            client_last_modified = clientModifiedSince,
            deleted = if (value == null) "1" else null,
        )
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val patchAdapter: JsonAdapter<SyncSettingsRequest> = moshi.adapter(SyncSettingsRequest::class.java).serializeNulls()
        }
    }
}

class SyncSettingsRequest(
    val settings: SyncSettingsUpdates,
    val client_timestamp: String,
)

class SyncSettingsUpdates(
    val updates: List<SettingEntry>,
    val modified_since: String,
)

data class SettingEntry(
    val key: String,
    val value: String,
    val deleted: String? = null,
    val client_last_modified: String?,
)
