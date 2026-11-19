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
import com.duckduckgo.sync.impl.DecryptedDevice
import com.duckduckgo.sync.impl.DeviceFieldDecryptor
import com.duckduckgo.sync.impl.DeviceInfoDecryptor
import com.duckduckgo.sync.impl.DeviceInfoPayload
import com.duckduckgo.sync.impl.DeviceInfoSessionResult
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.DEVICE_INFO_ADDED
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGIN
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGOUT
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource.INFO
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource.LATER_INFO
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource.LEGACY
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource.PLACEHOLDER
import dev.zacsweers.metro.ContributesBinding
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@WorkerThread
interface AuditLogDeviceNameResolver {
    /**
     * Returns a device name for every [AuditLogEntryType.LOGIN] and [AuditLogEntryType.LOGOUT] entry, keyed by entry id.
     *
     * [entries] must be newest first, as the server returns them. Pass every entry loaded so far (all pages),
     * because a login and the `device_info_added` that has its name can be on different pages.
     */
    fun resolve(entries: List<DecodedAuditLogEntry>): Map<String, ResolvedDeviceName>
}

data class ResolvedDeviceName(
    val name: String,
    val type: String?,
    val source: DeviceNameSource,
)

enum class DeviceNameSource {
    INFO,
    LATER_INFO,
    LEGACY,
    PLACEHOLDER,
}

@ContributesBinding(AppScope::class)
class RealAuditLogDeviceNameResolver @Inject constructor(
    private val deviceInfoDecryptor: DeviceInfoDecryptor,
    private val deviceFieldDecryptor: DeviceFieldDecryptor,
) : AuditLogDeviceNameResolver {

    override fun resolve(entries: List<DecodedAuditLogEntry>): Map<String, ResolvedDeviceName> {
        val decoded = entries.filterIsInstance<Decoded>()
        if (decoded.none { it.type in NAMED_TYPES }) return emptyMap()

        val session = when (val result = deviceInfoDecryptor.openSession()) {
            is DeviceInfoSessionResult.Available -> MemoisedSession(result.session)
            is DeviceInfoSessionResult.Unavailable -> null
        }
        // Input is newest first, so everything before an entry's index happened after it.
        val names = decoded.withIndex()
            .filter { it.value.type in NAMED_TYPES }
            .associate { (index, entry) -> entry.id to resolve(entry, newerEntries = decoded.subList(0, index), session) }

        logcat(VERBOSE) { "Sync-AuditLog: named ${names.size} entries ${names.values.groupingBy { it.source }.eachCount()}" }
        return names
    }

    /**
     * First match wins:
     * 1. the entry's own `info`;
     * 2. login only: a later `device_info_added` for the same device. A device gets the account_info key in its login response and
     *    only then writes its `info`, so a first login never carries one;
     * 3. the legacy `name`/`type`, encrypted with the device's credential;
     * 4. the same "Unknown device" placeholder as the device list.
     */
    private fun resolve(
        entry: Decoded,
        newerEntries: List<Decoded>,
        session: MemoisedSession?,
    ): ResolvedDeviceName {
        session?.decryptInfo(entry.device.deviceInfo)?.let { return it.toName(INFO) }

        if (entry.type == LOGIN && session != null) {
            laterInfoCandidates(entry.device.deviceId, newerEntries)
                .firstNotNullOfOrNull { session.decryptInfo(it.device.deviceInfo) }
                ?.let { return it.toName(LATER_INFO) }
        }

        val legacy = deviceFieldDecryptor.decrypt(entry.device)
        if (legacy is Result.Success) return ResolvedDeviceName(legacy.data.name, legacy.data.type, LEGACY)

        val placeholder = DecryptedDevice.placeholder(entry.device.deviceId.orEmpty(), entry.device.credentialId)
        return ResolvedDeviceName(placeholder.name, placeholder.type, PLACEHOLDER)
    }

    /** The `device_info_added` entries for [deviceId] after the login and before that device's next login, oldest first. */
    private fun laterInfoCandidates(deviceId: String?, newerEntries: List<Decoded>): List<Decoded> =
        newerEntries.asReversed()
            .filter { it.device.deviceId == deviceId }
            .takeWhile { it.type != LOGIN } // after its next login, a device_info_added gives the name for that login instead
            .filter { it.type == DEVICE_INFO_ADDED }

    /** A login's `info` is often repeated on later rows and tried again for each earlier login, and each decrypt is an RSA operation. */
    private class MemoisedSession(private val session: DeviceInfoDecryptor.Session) {
        private val cache = mutableMapOf<String, DeviceInfoPayload?>()

        fun decryptInfo(deviceInfo: String?): DeviceInfoPayload? {
            if (deviceInfo.isNullOrEmpty()) return null
            return cache.getOrPut(deviceInfo) { (session.decrypt(deviceInfo) as? Result.Success)?.data }
        }
    }

    private fun DeviceInfoPayload.toName(source: DeviceNameSource) = ResolvedDeviceName(name, type, source)

    private companion object {
        val NAMED_TYPES = setOf(LOGIN, LOGOUT)
    }
}
