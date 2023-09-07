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

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.settings.api.SyncableSetting
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.ModifiedSince
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncableDataProvider
import com.duckduckgo.sync.api.engine.SyncableType.SETTINGS
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.*
import timber.log.Timber

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataProvider::class)
class SettingsSyncDataProvider @Inject constructor(
    val syncableSettings: PluginPoint<SyncableSetting>,
    val settingsSyncMetadataDao: SettingsSyncMetadataDao,
    val settingsSyncStore: SettingsSyncStore,
    val syncCrypto: SyncCrypto,
) : SyncableDataProvider {
    override fun getChanges(): SyncChangesRequest {
        val syncableSettings = syncableSettings.getPlugins()
        if (settingsSyncStore.serverModifiedSince == "0") {
            val keys = syncableSettings.map { it.key }
            settingsSyncMetadataDao.initialize(keys)
        }

        val since = settingsSyncStore.clientModifiedSince
        val updates = getUpdatesSince(syncableSettings, since)
        Timber.i("Sync-Settings: getChanges() since=$since updates=$updates")
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
                setting.getValue()?.let {
                    Timber.i("Sync-Settings: adding all update key ${setting.key} value=$it")
                    updates.add(
                        SettingEntry(
                            key = setting.key,
                            value = syncCrypto.encrypt(it),
                            client_last_modified = SyncDateProvider.now(),
                        ),
                    )
                } ?: updates.add(
                    SettingEntry(
                        key = setting.key,
                        value = "",
                        deleted = "1",
                        client_last_modified = SyncDateProvider.now(),
                    ),
                )
            }
        } else {
            val settingsToUpdate = settingsSyncMetadataDao.getChangesSince(clientModifiedSince)
            // TODO: missing case for settings added later on, how to detect them?
            syncableSettings.forEach { setting ->
                val metadata = settingsToUpdate.find { it.key == setting.key } ?: return@forEach
                Timber.i("Sync-Settings: changes since=$clientModifiedSince metadata=$metadata")
                setting.getValue()?.let { value ->
                    Timber.i("Sync-Settings: adding changed update key ${setting.key} value=$value")
                    updates.add(
                        SettingEntry(
                            key = setting.key,
                            value = syncCrypto.encrypt(value),
                            client_last_modified = metadata.modified_at,
                        ),
                    )
                } ?: updates.add(
                    SettingEntry(
                        key = setting.key,
                        value = "",
                        deleted = "1",
                        client_last_modified = metadata.modified_at,
                    ),
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

        Timber.i("Sync-Settings: formatUpdates() modifiedSince=$modifiedSince updates=$updates")

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
