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
import com.duckduckgo.sync.impl.DeviceInfoDecryptor
import com.duckduckgo.sync.impl.DeviceInfoSessionResult
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.DEVICE_INFO_ADDED
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGIN
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

/** Finds the new logins and the late device names in one check's entries. */
@WorkerThread
interface PendingLoginsUpdater {
    /**
     * [entries] are one check's entries, newest first. [pendingLogins] is the stored list when the check started; it only decides
     * which late names are worth decrypting.
     */
    fun prepare(
        entries: List<DecodedAuditLogEntry>,
        ownDeviceId: String,
        pendingLogins: List<PendingLogin>,
    ): PendingLoginsUpdate
}

data class PendingLoginsUpdate(
    /** Logins by other devices, oldest first. */
    val newLogins: List<PendingLogin>,
    /** Device id to name, for pending logins from an earlier check. */
    val lateNames: Map<String, String>,
) {
    data class Applied(val pendingLogins: List<PendingLogin>, val added: Int, val renamed: Int)

    /**
     * Adds the logins that [current] does not already have, by entry id, and sets the late names.
     * Does no decrypts, so it is fast enough to run inside the store's lock, against the list as it is at write time.
     */
    fun applyTo(current: List<PendingLogin>): Applied {
        val known = current.mapTo(mutableSetOf()) { it.entryId }
        val added = newLogins.filter { it.entryId !in known }
        val renamed = current.updateDeviceName(lateNames)
        return Applied(
            pendingLogins = renamed + added,
            added = added.size,
            renamed = renamed.zip(current).count { (after, before) -> after != before },
        )
    }

    /**
     * Sets a late-arriving name on pending logins that were stored with a fallback name.
     *
     * A device's first login has no `info`: the device gets the account_info key in the login response, and only then writes
     * its `info`, which makes a `device_info_added` entry. If a check runs between the two, the login is stored with a fallback
     * name, and the real name only arrives in a later check.
     *
     * [names] maps device id to name. Only the **newest** pending row for each device gets the name, because the `info` belongs
     * to that device's most recent login, not to its earlier ones.
     */
    private fun List<PendingLogin>.updateDeviceName(names: Map<String, String>): List<PendingLogin> {
        if (names.isEmpty()) return this

        val latestIndexByDevice = withIndex().associate { (index, login) -> login.deviceId to index }
        return mapIndexed { index, login ->
            val name = names[login.deviceId]
            if (name != null && latestIndexByDevice[login.deviceId] == index) login.copy(deviceName = name) else login
        }
    }
}

@ContributesBinding(AppScope::class)
class RealPendingLoginsUpdater @Inject constructor(
    private val deviceNameResolver: AuditLogDeviceNameResolver,
    private val deviceInfoDecryptor: DeviceInfoDecryptor,
) : PendingLoginsUpdater {

    override fun prepare(
        entries: List<DecodedAuditLogEntry>,
        ownDeviceId: String,
        pendingLogins: List<PendingLogin>,
    ): PendingLoginsUpdate {
        val oldestFirst = entries.filterIsInstance<Decoded>().asReversed()
        return PendingLoginsUpdate(
            newLogins = newLogins(entries, oldestFirst, ownDeviceId),
            lateNames = lateNames(oldestFirst, pendingDevices = pendingLogins.mapTo(mutableSetOf()) { it.deviceId }),
        )
    }

    private fun newLogins(
        newestFirst: List<DecodedAuditLogEntry>,
        oldestFirst: List<Decoded>,
        ownDeviceId: String,
    ): List<PendingLogin> {
        val names = deviceNameResolver.resolve(newestFirst)
        return oldestFirst
            .filter { it.type == LOGIN && it.device.deviceId != ownDeviceId }
            .distinctBy { it.id }
            .map { it.toPendingLogin(names[it.id]) }
    }

    private fun lateNames(oldestFirst: List<Decoded>, pendingDevices: Set<String>): Map<String, String> {
        val infoByDevice = lateInfoByDevice(oldestFirst, pendingDevices)
        if (infoByDevice.isEmpty()) return emptyMap()

        val session = (deviceInfoDecryptor.openSession() as? DeviceInfoSessionResult.Available)?.session ?: return emptyMap()
        return infoByDevice.mapNotNull { (deviceId, infos) ->
            infos.firstNotNullOfOrNull { (session.decrypt(it) as? Result.Success)?.data?.name }?.let { deviceId to it }
        }.toMap()
    }

    /**
     * Per pending device, the `info` of its `device_info_added` entries, oldest first. An entry after a login of the same device
     * in this check is left out: it names that new login, which the resolver already handles.
     */
    private fun lateInfoByDevice(oldestFirst: List<Decoded>, pendingDevices: Set<String>): Map<String, List<String>> {
        val loggedInThisCheck = mutableSetOf<String>()
        val infoByDevice = linkedMapOf<String, MutableList<String>>()
        oldestFirst.forEach { entry ->
            val deviceId = entry.device.deviceId ?: return@forEach
            when {
                entry.type == LOGIN -> loggedInThisCheck += deviceId
                entry.type == DEVICE_INFO_ADDED && deviceId in pendingDevices && deviceId !in loggedInThisCheck ->
                    entry.device.deviceInfo?.let { infoByDevice.getOrPut(deviceId) { mutableListOf() } += it }
            }
        }
        return infoByDevice
    }

    private fun Decoded.toPendingLogin(name: ResolvedDeviceName?): PendingLogin {
        val deviceId = device.deviceId.orEmpty()
        return PendingLogin(
            entryId = id,
            deviceId = deviceId,
            deviceName = name?.name ?: DecryptedDevice.placeholder(deviceId, device.credentialId).name,
            occurredAt = occurredAt,
        )
    }
}
