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

import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.AccountInfoKeyUnavailableReason
import com.duckduckgo.sync.impl.AccountInfoPrivateKeyProvider
import com.duckduckgo.sync.impl.AccountInfoPrivateKeyResult
import com.duckduckgo.sync.impl.AuditLogEntry
import com.duckduckgo.sync.impl.DeviceV2
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason.KEY_NOT_FOUND
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason.PAYLOAD_DECRYPT_ERROR
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason.UNKNOWN_TYPE
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason.UNKNOWN_VERSION
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Skipped
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.ContributesBinding
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.time.Instant
import javax.inject.Inject

@WorkerThread
interface AuditLogEntryDecoder {
    /** Returns one result per entry, in the same order. The account_info private key is fetched at most once per call. */
    fun decode(entries: List<AuditLogEntry>): AuditLogDecodeResult
}

/**
 * [keyUnavailableReason] is set when the account_info key could not be obtained, so every readable entry was skipped as
 * [AuditLogEntrySkipReason.KEY_NOT_FOUND]. Some reasons are temporary (rate limit, fetch failure): a poller should then keep its cursor.
 */
data class AuditLogDecodeResult(
    val entries: List<DecodedAuditLogEntry>,
    val keyUnavailableReason: AccountInfoKeyUnavailableReason? = null,
)

sealed interface DecodedAuditLogEntry {
    val id: String

    /** [actor] is the device that did the logout; it is only set for [AuditLogEntryType.LOGOUT]. */
    data class Decoded(
        override val id: String,
        val type: AuditLogEntryType,
        val occurredAt: Instant,
        val device: DeviceV2,
        val actor: DeviceV2?,
    ) : DecodedAuditLogEntry

    data class Skipped(
        override val id: String,
        val reason: AuditLogEntrySkipReason,
    ) : DecodedAuditLogEntry
}

enum class AuditLogEntryType {
    LOGIN,
    LOGOUT,
    DEVICE_INFO_ADDED,
}

enum class AuditLogEntrySkipReason {
    UNKNOWN_VERSION,
    KEY_NOT_FOUND,
    PAYLOAD_DECRYPT_ERROR,
    UNKNOWN_TYPE,
}

@ContributesBinding(AppScope::class)
class RealAuditLogEntryDecoder @Inject constructor(
    private val accountInfoPrivateKeyProvider: AccountInfoPrivateKeyProvider,
    private val syncJweCrypto: SyncJweCrypto,
) : AuditLogEntryDecoder {

    override fun decode(entries: List<AuditLogEntry>): AuditLogDecodeResult {
        val keyResult = lazy { accountInfoPrivateKeyProvider.privateKey() }
        val decoded = entries.map { entry ->
            if (entry.version != SUPPORTED_VERSION) {
                Skipped(entry.id, UNKNOWN_VERSION)
            } else {
                when (val key = keyResult.value) {
                    is AccountInfoPrivateKeyResult.Available -> decode(entry, key.privateKey)
                    is AccountInfoPrivateKeyResult.Unavailable -> Skipped(entry.id, KEY_NOT_FOUND)
                }
            }
        }
        logcat(VERBOSE) {
            "Sync-AuditLog: decoded ${decoded.count { it is Decoded }} of ${entries.size} entries; " +
                "skipped ${decoded.filterIsInstance<Skipped>().groupingBy { it.reason }.eachCount()}"
        }
        return AuditLogDecodeResult(
            entries = decoded,
            keyUnavailableReason = keyResult.takeIf { it.isInitialized() }?.let { (it.value as? AccountInfoPrivateKeyResult.Unavailable)?.reason },
        )
    }

    private fun decode(entry: AuditLogEntry, privateKey: String): DecodedAuditLogEntry {
        val payload = runCatching {
            payloadAdapter.fromJson(String(syncJweCrypto.jweDecryptRsaOaep(entry.payload, privateKey), Charsets.UTF_8))
        }.getOrNull() ?: return Skipped(entry.id, PAYLOAD_DECRYPT_ERROR)

        val type = when (payload.type) {
            "login" -> AuditLogEntryType.LOGIN
            "logout" -> AuditLogEntryType.LOGOUT
            "device_info_added" -> AuditLogEntryType.DEVICE_INFO_ADDED
            else -> return Skipped(entry.id, UNKNOWN_TYPE)
        }
        val occurredAt = payload.occurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val device = payload.device?.takeUnless { it.deviceId.isNullOrEmpty() }
        if (occurredAt == null || device == null) return Skipped(entry.id, PAYLOAD_DECRYPT_ERROR)

        return Decoded(entry.id, type, occurredAt, device, payload.actor)
    }

    private class AuditLogPayload(
        val type: String? = null,
        @field:Json(name = "occurred_at") val occurredAt: String? = null,
        val device: DeviceV2? = null,
        val actor: DeviceV2? = null,
    )

    private companion object {
        const val SUPPORTED_VERSION = "1"

        // Not KotlinJsonAdapterFactory: it ignores the @field:Json names on DeviceV2, so every device field would parse as null.
        val payloadAdapter: JsonAdapter<AuditLogPayload> by lazy { Moshi.Builder().build().adapter(AuditLogPayload::class.java) }
    }
}
