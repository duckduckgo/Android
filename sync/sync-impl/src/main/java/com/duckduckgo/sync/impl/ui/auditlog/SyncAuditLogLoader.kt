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
import com.duckduckgo.sync.impl.auditlog.AuditLogPixels
import com.duckduckgo.sync.impl.auditlog.AuditLogResult
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.fireFetchErrorIfFailed
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import java.time.Instant
import javax.inject.Inject

interface SyncAuditLogLoader {
    /**
     * Reads the newest entries of the audit log. If a page has no rows to show, older pages are read until one has, the log ends,
     * or a per-load page limit is reached; so [SyncAuditLogLoadResult.Loaded.items] can be empty while there is more.
     * Never touches the cursor that the unseen-logins check stores.
     */
    suspend fun loadLatest(entriesPerPage: Int? = null): SyncAuditLogLoadResult

    /** Reads the entries older than the ones in [loaded], with the same rules as [loadLatest]. */
    suspend fun loadOlder(
        loaded: SyncAuditLogLoadResult.Loaded,
        entriesPerPage: Int? = null,
    ): SyncAuditLogLoadResult
}

sealed interface SyncAuditLogLoadResult {
    /** [items] are the rows this call found, newest first. Pass this result to [SyncAuditLogLoader.loadOlder] to read further. */
    data class Loaded(
        val items: List<SyncAuditLogItem>,
        val pages: SyncAuditLogPages,
    ) : SyncAuditLogLoadResult {
        val hasMore: Boolean get() = pages.before != null
    }

    data object Failed : SyncAuditLogLoadResult
}

/**
 * Every entry read so far, newest first, and the cursor for older entries (null at the start of the log).
 * Kept in memory only: a login's name can come from a `device_info_added` on a newer page.
 */
data class SyncAuditLogPages(
    val entries: List<DecodedAuditLogEntry>,
    val before: String?,
)

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
    private val auditLogPixels: AuditLogPixels,
    private val dispatchers: DispatcherProvider,
) : SyncAuditLogLoader {

    override suspend fun loadLatest(entriesPerPage: Int?): SyncAuditLogLoadResult = withTokenCatching { token ->
        load(token, SyncAuditLogPages(entries = emptyList(), before = null), firstCursor = null, entriesPerPage)
    }

    override suspend fun loadOlder(
        loaded: SyncAuditLogLoadResult.Loaded,
        entriesPerPage: Int?,
    ): SyncAuditLogLoadResult = withTokenCatching { token ->
        val before = loaded.pages.before ?: return@withTokenCatching loaded.copy(items = emptyList())
        load(token, loaded.pages, firstCursor = before, entriesPerPage)
    }

    private suspend fun withTokenCatching(
        block: suspend (token: String) -> SyncAuditLogLoadResult,
    ): SyncAuditLogLoadResult = withContext(dispatchers.io()) {
        // Keystore, native crypto and parsing sit below this; the screen has an error state, so a missed throw must not crash it.
        try {
            val token = syncStore.token
            if (token.isNullOrEmpty()) SyncAuditLogLoadResult.Failed else block(token)
        } catch (e: Exception) {
            ensureActive()
            logcat(WARN) { "Sync-AuditLog: device activity load threw: ${e.javaClass.simpleName}" }
            SyncAuditLogLoadResult.Failed
        }
    }

    /** [firstCursor] null reads the newest page. [limit] null uses the server's default page size. */
    private suspend fun load(
        token: String,
        loaded: SyncAuditLogPages,
        firstCursor: String?,
        limit: Int?,
    ): SyncAuditLogLoadResult {
        var pages = loaded
        var cursor = firstCursor
        val loadedIds = loaded.entries.mapTo(HashSet()) { it.id }
        repeat(MAX_PAGES_PER_LOAD) {
            val result = cursor?.let { syncApi.getAuditLogBefore(token, it, limit) } ?: syncApi.getAuditLogLatest(token, limit)
            if (result !is AuditLogResult.Success) {
                auditLogPixels.fireFetchErrorIfFailed(result)
                logcat(WARN) { "Sync-AuditLog: device activity load failed: ${result::class.simpleName}" }
                return SyncAuditLogLoadResult.Failed
            }

            val decodeResult = entryDecoder.decode(result.page.entries)
            // Without the key every entry is skipped, and an empty list would wrongly tell the user there was no activity.
            decodeResult.keyUnavailableReason?.let { reason ->
                logcat(WARN) { "Sync-AuditLog: device activity key unavailable: $reason" }
                return SyncAuditLogLoadResult.Failed
            }

            val newEntries = decodeResult.entries.filterNot { it.id in loadedIds }
            newEntries.mapTo(loadedIds) { it.id }
            val names = nameResolver.resolve(newEntries, newerEntries = pages.entries)
            val items = newEntries
                .filterIsInstance<Decoded>()
                .mapNotNull { entry -> names[entry.id]?.let { name -> entry.toItem(name.name, name.type) } }
            pages = SyncAuditLogPages(pages.entries + newEntries, result.page.before)

            if (items.isNotEmpty() || pages.before == null) return SyncAuditLogLoadResult.Loaded(items, pages)
            cursor = pages.before
        }
        return SyncAuditLogLoadResult.Loaded(emptyList(), pages)
    }

    private fun Decoded.toItem(name: String, type: String?): SyncAuditLogItem? {
        val action = when (this.type) {
            LOGIN -> SyncAuditLogAction.SYNCED
            LOGOUT -> SyncAuditLogAction.REMOVED
            DEVICE_INFO_ADDED -> return null
        }
        return SyncAuditLogItem(id, action, occurredAt, name, DeviceType(type.orEmpty()))
    }

    companion object {
        // A long run of hidden or unreadable entries must not use up the 60 requests per minute rate limit in one go.
        const val MAX_PAGES_PER_LOAD = 10
    }
}
