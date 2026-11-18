/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.auditlog

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.auditlog.AuditLogDeviceNameResolver
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryDecoder
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.DEVICE_INFO_ADDED
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGIN
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGOUT
import com.duckduckgo.sync.impl.auditlog.AuditLogResult
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import java.time.Instant
import javax.inject.Inject

interface SyncAuditLogLoader {
    /** Reads the newest page of the audit log. Never touches the cursor that the unseen-logins check stores. */
    suspend fun loadLatest(): SyncAuditLogLoadResult
}

sealed interface SyncAuditLogLoadResult {
    /** [items] are newest first. */
    data class Loaded(val items: List<SyncAuditLogItem>) : SyncAuditLogLoadResult
    data object Failed : SyncAuditLogLoadResult
}

data class SyncAuditLogItem(
    val entryId: String,
    val action: SyncAuditLogAction,
    val occurredAt: Instant,
    val deviceName: String,
    val deviceType: DeviceType,
)

enum class SyncAuditLogAction {
    SYNCED,
    REMOVED,
}

@ContributesBinding(AppScope::class)
class RealSyncAuditLogLoader @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val entryDecoder: AuditLogEntryDecoder,
    private val nameResolver: AuditLogDeviceNameResolver,
    private val dispatchers: DispatcherProvider,
) : SyncAuditLogLoader {

    override suspend fun loadLatest(): SyncAuditLogLoadResult = withContext(dispatchers.io()) {
        // Keystore, native crypto and parsing sit below this; the screen has an error state, so a missed throw must not crash it.
        try {
            load()
        } catch (e: Exception) {
            ensureActive()
            logcat(WARN) { "Sync-AuditLog: device activity load threw: ${e.javaClass.simpleName}" }
            SyncAuditLogLoadResult.Failed
        }
    }

    private fun load(): SyncAuditLogLoadResult {
        val token = syncStore.token
        if (token.isNullOrEmpty()) return SyncAuditLogLoadResult.Failed

        val result = syncApi.getAuditLogLatest(token)
        if (result !is AuditLogResult.Success) {
            logcat(WARN) { "Sync-AuditLog: device activity load failed: ${result::class.simpleName}" }
            return SyncAuditLogLoadResult.Failed
        }

        val decodeResult = entryDecoder.decode(result.page.entries)
        // Without the key every entry is skipped, and an empty list would wrongly tell the user there was no activity.
        decodeResult.keyUnavailableReason?.let { reason ->
            logcat(WARN) { "Sync-AuditLog: device activity key unavailable: $reason" }
            return SyncAuditLogLoadResult.Failed
        }

        val names = nameResolver.resolve(decodeResult.entries)
        val items = decodeResult.entries
            .filterIsInstance<Decoded>()
            .mapNotNull { entry -> names[entry.id]?.let { name -> entry.toItem(name.name, name.type) } }
        return SyncAuditLogLoadResult.Loaded(items)
    }

    private fun Decoded.toItem(name: String, type: String?): SyncAuditLogItem? {
        val action = when (this.type) {
            LOGIN -> SyncAuditLogAction.SYNCED
            LOGOUT -> SyncAuditLogAction.REMOVED
            DEVICE_INFO_ADDED -> return null
        }
        return SyncAuditLogItem(id, action, occurredAt, name, DeviceType(type.orEmpty()))
    }
}
