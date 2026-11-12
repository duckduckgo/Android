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

package com.duckduckgo.sync.internal.ui

import androidx.annotation.WorkerThread
import com.duckduckgo.sync.impl.DeviceV2
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.auditlog.AuditLogDeviceNameResolver
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryDecoder
import com.duckduckgo.sync.impl.auditlog.AuditLogResult
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Skipped
import com.duckduckgo.sync.impl.auditlog.ResolvedDeviceName
import logcat.logcat
import javax.inject.Inject

class AuditLogDebugReport @Inject constructor(
    private val syncApi: SyncApi,
    private val decoder: AuditLogEntryDecoder,
    private val resolver: AuditLogDeviceNameResolver,
) {

    @WorkerThread
    fun fetchAndDescribe(token: String, ownDeviceId: String?): String {
        val result = syncApi.getAuditLogLatest(token)
        if (result !is AuditLogResult.Success) {
            logcat { "Sync-AuditLog: internal fetch failed: $result" }
            return "Error: $result"
        }

        val decodeResult = decoder.decode(result.page.entries)
        val entries = decodeResult.entries
        val names = resolver.resolve(entries)
        val skipped = entries.count { it is Skipped }
        val header = buildString {
            append("${entries.size} entries: ${entries.size - skipped} decoded, $skipped skipped. ")
            append("Older entries: ${if (result.page.before != null) "yes" else "no"}")
            decodeResult.keyUnavailableReason?.let { append("\naccount_info key unavailable: $it") }
        }
        val text = (listOf(header) + entries.map { describe(it, names[it.id], ownDeviceId) }).joinToString("\n\n")
        logcat { "Sync-AuditLog: internal fetch:\n$text" }
        return text
    }

    private fun describe(entry: DecodedAuditLogEntry, name: ResolvedDeviceName?, ownDeviceId: String?): String = when (entry) {
        is Skipped -> "SKIPPED  ${entry.reason}\n  entry: ${entry.id}"
        is Decoded -> buildString {
            append("${entry.occurredAt}\n  ${entry.type}")
            name?.let { append("\n  name: ${it.name}${it.type?.let { type -> " ($type)" }.orEmpty()} [${it.source}]") }
            append("\n  device: ${describeDevice(entry.device, ownDeviceId)} [credential=${entry.device.credentialId ?: "ddg"}]")
            entry.actor?.let { append("\n  actor: ${describeDevice(it, ownDeviceId)}") }
        }
    }

    private fun describeDevice(device: DeviceV2, ownDeviceId: String?): String {
        val marker = if (device.deviceId != null && device.deviceId == ownDeviceId) " (this device)" else ""
        return "${device.deviceId ?: "(no id)"}$marker"
    }
}
