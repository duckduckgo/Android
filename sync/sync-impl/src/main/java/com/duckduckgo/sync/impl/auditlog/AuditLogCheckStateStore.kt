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

package com.duckduckgo.sync.impl.auditlog

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.store.SyncStore
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class PendingLogin(
    val entryId: String,
    val deviceId: String,
    val deviceName: String,
    val occurredAt: Instant,
)

data class AuditLogCheckState(
    /** The last `after` cursor. */
    val cursor: String? = null,
    /** Oldest first. */
    val pendingLogins: List<PendingLogin> = emptyList(),
)

/**
 * Stored state of the audit log checker. It lives in [SyncStore], so signing out clears it.
 * All reads and writes share one lock, so every caller gets an atomic read-modify-write.
 * Unreadable stored data reads as an empty list.
 */
interface AuditLogCheckStateStore {
    suspend fun read(): AuditLogCheckState

    /**
     * Reads the current state, passes it to [transform] and stores the result. Return null from [transform] to write nothing.
     * Nothing is written while signed out. A write keeps only the newest [MAX_PENDING_LOGINS] pending logins.
     */
    suspend fun update(transform: (AuditLogCheckState) -> AuditLogCheckState?)

    companion object {
        const val MAX_PENDING_LOGINS = 100
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAuditLogCheckStateStore @Inject constructor(
    private val syncStore: SyncStore,
    private val dispatchers: DispatcherProvider,
) : AuditLogCheckStateStore {

    private val mutex = Mutex()

    override suspend fun read(): AuditLogCheckState = withContext(dispatchers.io()) {
        mutex.withLock { readState() }
    }

    override suspend fun update(transform: (AuditLogCheckState) -> AuditLogCheckState?) = withContext(dispatchers.io()) {
        mutex.withLock {
            val updated = transform(readState()) ?: return@withLock
            // Sign-out wipes SyncStore outside this lock; without this check a late write would bring back the old account's state.
            if (!syncStore.isSignedIn()) return@withLock

            // List before cursor: if the process dies between the two writes, the next check repeats from the old cursor,
            // and the caller dedupes by entry id. The other order would lose those logins.
            syncStore.auditLogPending = encode(updated.pendingLogins)
            syncStore.auditLogCursor = updated.cursor
        }
    }

    private fun readState() = AuditLogCheckState(cursor = syncStore.auditLogCursor, pendingLogins = decode(syncStore.auditLogPending))

    private fun encode(logins: List<PendingLogin>): String? {
        val capped = logins.takeLast(AuditLogCheckStateStore.MAX_PENDING_LOGINS)
        return if (capped.isEmpty()) null else adapter.toJson(capped.map { it.toJson() })
    }

    private fun decode(json: String?): List<PendingLogin> {
        if (json.isNullOrEmpty()) return emptyList()
        val rows = try {
            adapter.fromJson(json)
        } catch (e: IOException) {
            null
        } catch (e: JsonDataException) {
            null
        }
        if (rows == null) {
            logcat(WARN) { "Sync-AuditLog: stored pending list is unreadable, reading it as empty" }
            return emptyList()
        }
        return rows.mapNotNull { it?.toPendingLogin() }
    }

    // Fields are nullable because the reflective adapter leaves a missing key as null, whatever the declared type.
    private data class PendingLoginJson(
        @field:Json(name = "entry_id") val entryId: String?,
        @field:Json(name = "device_id") val deviceId: String?,
        @field:Json(name = "device_name") val deviceName: String?,
        @field:Json(name = "occurred_at") val occurredAt: String?,
    ) {
        fun toPendingLogin(): PendingLogin? {
            return PendingLogin(
                entryId = entryId ?: return null,
                deviceId = deviceId ?: return null,
                deviceName = deviceName ?: return null,
                occurredAt = occurredAt?.toInstantOrNull() ?: return null,
            )
        }
    }

    private fun PendingLogin.toJson() = PendingLoginJson(entryId, deviceId, deviceName, occurredAt.toString())

    private companion object {
        fun String.toInstantOrNull(): Instant? = try {
            Instant.parse(this)
        } catch (e: DateTimeParseException) {
            null
        }

        val adapter: JsonAdapter<List<PendingLoginJson?>> by lazy {
            Moshi.Builder().build().adapter(Types.newParameterizedType(List::class.java, PendingLoginJson::class.java))
        }
    }
}
