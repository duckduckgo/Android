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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.AccountInfoKeyManager
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.DeviceFieldEncryptor
import com.duckduckgo.sync.impl.DeviceInfoEncryptor
import com.duckduckgo.sync.impl.DeviceUpdate
import com.duckduckgo.sync.impl.PatchDevicesRequest
import com.duckduckgo.sync.impl.RemoteDeviceLogout
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.SyncService
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesBinding
import logcat.logcat
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class OtherDevices(val devices: List<ConnectedDevice>, val error: String? = null)

/**
 * Lists and logs out the other devices on the signed-in account, and logs in extra devices with no real device behind
 * them, so the audit log gets real `login`, `device_info_added` and `logout` entries to test with.
 */
class OtherDevicesDebugTool @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncLib: SyncLib,
    private val deviceFieldEncryptor: DeviceFieldEncryptor,
    private val deviceInfoEncryptor: DeviceInfoEncryptor,
    private val accountInfoKeyManager: AccountInfoKeyManager,
    private val patcher: FakeDevicePatcher,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val remoteDeviceLogout: RemoteDeviceLogout,
    private val currentTimeProvider: CurrentTimeProvider,
) {

    fun otherDevices(): OtherDevices {
        if (!syncStore.isSignedIn()) return OtherDevices(devices = emptyList())
        return when (val result = connectedDevicesRepository.getConnectedDevices(reportAccountErrorPixel = false)) {
            is Success -> OtherDevices(devices = result.data.filterNot { it.thisDevice })
            is Error -> OtherDevices(devices = emptyList(), error = log("Could not get the device list: ${result.reason}"))
        }
    }

    suspend fun simulateLogin(): String {
        if (!syncStore.isSignedIn()) return "Not signed in"
        val userId = syncStore.userId ?: return "Not signed in"
        val primaryKey = syncStore.primaryKey ?: return "Not signed in"
        if ((syncStore.credentialId ?: CREDENTIAL_ID_DDG) != CREDENTIAL_ID_DDG) {
            return "Only works on a DuckDuckGo Sync account, not a third-party one."
        }
        if (syncStore.accountInfoPublicKey == null) {
            val keyResult = accountInfoKeyManager.ensureKeyRegistered()
            if (keyResult is Error) return "No account_info key: ${keyResult.reason}"
        }

        val name = "Fake device ${NAME_TIME_FORMAT.format(Instant.ofEpochMilli(currentTimeProvider.currentTimeMillis()))}"
        val legacyFields = when (val result = deviceFieldEncryptor.encrypt(name, DEVICE_TYPE)) {
            is Success -> result.data
            is Error -> return "Encrypting the name failed: ${result.reason}"
        }
        val deviceInfo = when (val result = deviceInfoEncryptor.encrypt(name, DEVICE_TYPE)) {
            is Success -> result.data
            is Error -> return "Encrypting device_info failed: ${result.reason}"
        }
        val loginKeys = syncLib.prepareForLogin(primaryKey)
        if (loginKeys.result != 0) return "Preparing the login failed (code ${loginKeys.result})"

        val deviceId = UUID.randomUUID().toString()
        val fakeToken = when (val result = syncApi.login(userId, loginKeys.passwordHash, deviceId, legacyFields.name, legacyFields.type)) {
            is Success -> result.data.token
            is Error -> return log("Login failed: ${result.reason} (code ${result.code})")
        }

        val update = DeviceUpdate(id = deviceId, name = legacyFields.name, type = legacyFields.type, info = deviceInfo)
        return when (val result = patcher.patch(fakeToken, update)) {
            is Success -> log("Logged in \"$name\" ($deviceId) and set its name.")
            is Error -> log(
                "Logged in \"$name\" ($deviceId), but setting its name failed: ${result.reason}. " +
                    "The audit log will show a placeholder name for it.",
            )
        }
    }

    fun logOut(device: ConnectedDevice): String =
        when (val result = remoteDeviceLogout.logout(device.deviceId, reportAccountErrorPixel = false)) {
            is Success -> log("Logged out \"${device.deviceName}\"")
            is Error -> log("Log out failed: ${result.reason}")
        }

    private fun log(text: String): String {
        logcat { "Sync-AuditLog: other devices tool: $text" }
        return text
    }

    private companion object {
        const val CREDENTIAL_ID_DDG = "ddg"
        const val DEVICE_TYPE = "desktop"
        val NAME_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}

/** Sends a fake device's name with the fake device's own token. `SyncApi.patchThisDevice` can only update this device. */
interface FakeDevicePatcher {
    fun patch(token: String, update: DeviceUpdate): Result<Unit>
}

@ContributesBinding(AppScope::class)
class RealFakeDevicePatcher @Inject constructor(
    private val syncService: SyncService,
) : FakeDevicePatcher {
    override fun patch(token: String, update: DeviceUpdate): Result<Unit> {
        val response = try {
            syncService.patchDevices("Bearer $token", PatchDevicesRequest(listOf(update))).execute()
        } catch (e: IOException) {
            return Error(reason = e.message ?: e.javaClass.simpleName)
        }
        return if (response.isSuccessful) Success(Unit) else Error(code = response.code(), reason = "HTTP ${response.code()}")
    }
}
