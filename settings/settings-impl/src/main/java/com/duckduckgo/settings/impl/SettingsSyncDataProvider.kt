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

import com.duckduckgo.di.scopes.*
import com.duckduckgo.settings.api.*
import com.duckduckgo.sync.api.*
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.SyncableType.SETTINGS
import com.squareup.anvil.annotations.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import timber.log.*
import javax.inject.*

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataProvider::class)
class SettingsSyncDataProvider @Inject constructor(
    val duckAddress: SyncableSetting,
    val settingsSyncMetadataDao: SettingsSyncMetadataDao,
    val settingsSyncStore: SettingsSyncStore,
    val syncCrypto: SyncCrypto,
) : SyncableDataProvider {
    override fun getChanges(): SyncChangesRequest {
        if (settingsSyncStore.serverModifiedSince=="0") {
            val keys = listOf(duckAddress.key)
            settingsSyncMetadataDao.initialize(keys)
        }

        val since = settingsSyncStore.clientModifiedSince
        val updates = getUpdatesSince(since)
        Timber.i("Sync-Settings: getChanges() since=$since updates=${updates}")
        return formatUpdates(updates)
    }

    private fun getUpdatesSince(clientModifiedSince: String): List<SettingEntry> {
        settingsSyncStore.startTimeStamp = SyncDateProvider.now()

        val updates = mutableListOf<SettingEntry>()
        if (clientModifiedSince=="0") {
            duckAddress.getValue()?.let {
                Timber.i("Sync-Settings: adding all update key ${duckAddress.key} value=$it")
                updates.add(SettingEntry(
                    key = duckAddress.key,
                    value = syncCrypto.encrypt(it),
                    client_last_modified = SyncDateProvider.now(),
                ))
            } ?: updates.add(SettingEntry(
                key = duckAddress.key,
                value = "",
                deleted = "1",
                client_last_modified = SyncDateProvider.now(),
            ))
        } else {
            //TODO: missing case for settings added later on, how to detect them?
            settingsSyncMetadataDao.getChangesSince(clientModifiedSince).forEach { metadata ->
                Timber.i("Sync-Settings: changes since=$clientModifiedSince metadata=${metadata}")
                if (metadata.key==duckAddress.key) {
                    duckAddress.getValue()?.let { value ->
                        Timber.i("Sync-Settings: adding changed update key ${duckAddress.key} value=$value")
                        updates.add(SettingEntry(
                            key = duckAddress.key,
                            value = syncCrypto.encrypt(value),
                            client_last_modified = metadata.modified_at,
                        ))
                    } ?: updates.add(SettingEntry(
                        key = duckAddress.key,
                        value = "",
                        deleted = "1",
                        client_last_modified = metadata.modified_at,
                    ))
                }
            }
            if (settingsSyncMetadataDao.get(duckAddress.key) == null) {
                duckAddress.getValue()?.let { value ->
                    Timber.i("Sync-Settings: adding new update key ${duckAddress.key} value=$value")
                    updates.add(SettingEntry(
                        key = duckAddress.key,
                        value = syncCrypto.encrypt(value),
                        client_last_modified = SyncDateProvider.now(),
                    ))
                }
            }
        }

        return updates
    }

    private fun formatUpdates(updates: List<SettingEntry>): SyncChangesRequest {
        val modifiedSince = if (settingsSyncStore.serverModifiedSince=="0") {
            ModifiedSince.FirstSync
        } else {
            ModifiedSince.Timestamp(settingsSyncStore.serverModifiedSince)
        }

        Timber.i("Sync-Settings: formatUpdates() modifiedSince=$modifiedSince updates=${updates}")

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
