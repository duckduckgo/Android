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

package com.duckduckgo.sync.impl

import androidx.annotation.WorkerThread
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.pixels.SyncAccountOperation
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

interface ConnectedDevicesRepository {
    /**
     * Fetches and decrypts the account's device list, with this device first.
     *
     * An [Error] keeps the code it failed with: an HTTP status or server error code when the server rejected the request, and
     * [AccountErrorCodes.GENERIC_ERROR] for everything else, such as no connection, an empty response, or missing credentials.
     */
    fun getConnectedDevices(): Result<List<ConnectedDevice>>

    /**
     * Lets callers observe how many devices are on the sync account, this one included, without fetching the device list.
     * The count reflects the last successful [getConnectedDevices], so it can be stale until the next one, and is 0 when this
     * device is signed out.
     */
    fun latestConnectedDeviceCountFlow(): Flow<Int>

    /**
     * The current value of [latestConnectedDeviceCountFlow], for callers that need it once without collecting.
     */
    suspend fun latestConnectedDeviceCount(): Int
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@WorkerThread
class RealConnectedDevicesRepository @Inject constructor(
    private val syncDeviceIds: SyncDeviceIds,
    private val nativeLib: SyncLib,
    private val syncApi: SyncApi,
    private val syncStore: SyncStore,
    private val syncPixels: SyncPixels,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncFeature: SyncFeature,
    private val thirdPartyDeviceListDecryptor: ThirdPartyDeviceListDecryptor,
    private val deviceInfoUpdater: DeviceInfoUpdater,
    private val deviceInfoPublishWatcher: DeviceInfoPublishWatcher,
    private val accountInfoDdgWrapRepairer: AccountInfoDdgWrapRepairer,
    private val remoteDeviceLogout: RemoteDeviceLogout,
) : ConnectedDevicesRepository {

    // The user id we've already re-published device_info for. Stops us repairing again every time the device list is shown.
    private val republishedDeviceInfoForUserId = AtomicReference<String?>(null)

    // Same latch shape for a missing ddg account_info wrap. Released on failure so the next list load can retry.
    private val repairedAccountInfoDdgWrapForUserId = AtomicReference<String?>(null)

    override fun latestConnectedDeviceCountFlow(): Flow<Int> = syncStore.connectedDevicesCountFlow()

    override suspend fun latestConnectedDeviceCount(): Int {
        return withContext(dispatcherProvider.io()) {
            syncStore.connectedDevicesCount
        }
    }

    override fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "Token Empty")
        val primaryKey = syncStore.primaryKey.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "PrimaryKey not found")

        val userId = syncStore.userId
        val result = if (syncFeature.canUseV2ConnectFlow().isEnabled()) {
            getConnectedDevicesV2(token, primaryKey)
        } else {
            getConnectedDevicesLegacy(token, primaryKey)
        }
        // A fetch can still be in flight when the user signs out or switches account. Comparing the user id covers both: sign-out clears
        // it and a switch replaces it, so we never undo the reset to 0 or write the old account's count over the new one.
        if (result is Success && userId != null && syncStore.userId == userId) {
            syncStore.connectedDevicesCount = result.data.size
        }
        return result
    }

    private fun getConnectedDevicesV2(
        token: String,
        primaryKey: String,
    ): Result<List<ConnectedDevice>> {
        val publishSnapshot = deviceInfoPublishWatcher.snapshot()
        return when (val result = syncApi.getDevices(token)) {
            is Error -> result.alsoFireAccountErrorPixel()
            is Success -> {
                val entriesV2 = result.data.entriesV2
                val devices = if (entriesV2 != null) {
                    val decryptResult = thirdPartyDeviceListDecryptor.decryptAll(entriesV2, syncStore.deviceId, publishSnapshot)
                    logoutFailedV2Devices(decryptResult.undecryptable)
                    fireUnifiedDeviceListReadPixels(decryptResult)
                    if (decryptResult.keyUnavailableReason == AccountInfoKeyUnavailableReason.NO_WRAP_FOR_OUR_CREDENTIAL) {
                        repairAccountInfoDdgWrap()
                    }
                    if (decryptResult.thisDeviceInfoNeedsRepair) republishThisDeviceInfo()
                    decryptResult.decrypted.map { it.toConnectedDevice() }
                } else {
                    // entries_v2 missing
                    decryptLegacyEntries(result.data.entries, primaryKey)
                }
                finishWith(devices)
            }
        }
    }

    private fun fireUnifiedDeviceListReadPixels(result: DecryptAllResult) {
        if (!syncFeature.canReadUnifiedDeviceList().isEnabled()) return

        result.keyUnavailableReason?.let {
            syncPixels.fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.AccountInfoKeyUnavailable(it))
            return
        }

        when (val outcome = result.ownDeviceReadOutcome) {
            OwnDeviceReadOutcome.ResolvedDeviceInfo ->
                syncPixels.fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.OwnRowResolvedDeviceInfo)
            is OwnDeviceReadOutcome.ResolvedLegacy -> fireOwnRowFallbackIfWritable(
                UnifiedDeviceListPixel.OwnRowResolvedLegacy(outcome.reason),
            )
            is OwnDeviceReadOutcome.ResolvedPlaceholder -> fireOwnRowFallbackIfWritable(
                UnifiedDeviceListPixel.OwnRowResolvedPlaceholder(outcome.reason),
            )
            null -> {}
        }

        result.otherRowFailedDecryptionCredentials.forEach {
            syncPixels.fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.OtherRowDeviceInfoFailedDecryption(it))
        }
        result.otherRowPlaceholderCredentials.forEach {
            syncPixels.fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.OtherRowResolvedPlaceholder(it))
        }
    }

    private fun fireOwnRowFallbackIfWritable(event: UnifiedDeviceListPixel) {
        if (syncFeature.canWriteUnifiedDeviceList().isEnabled()) {
            syncPixels.fireUnifiedDeviceListPixel(event)
        }
    }

    /**
     * Re-writes this device's `device_info` with the name it already has, after rendering the device list showed that our own blob is missing or
     * undecryptable. A flag-off rename is the expected cause: it omits `device_info`, so the server clears it, and migration's done-marker is a
     * latch that won't put it back.
     *
     * Best-effort, so a failure never affects the list the caller is building; a failed attempt is retried on the next load.
     */
    private fun republishThisDeviceInfo() {
        // an ungated repair would omit device_info and so delete the blob it is trying to restore
        if (!syncFeature.canWriteDeviceInfo()) return
        val userId = syncStore.userId ?: return
        // keyed by account rather than latched outright, so signing into a different account in the same process can still repair
        if (republishedDeviceInfoForUserId.getAndSet(userId) == userId) return

        appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat { "Sync-UnifiedDevices: this device's device_info did not resolve on read; re-publishing it" }
            when (
                val result = deviceInfoUpdater.setThisDeviceName(
                    name = syncDeviceIds.deviceName(),
                    source = DeviceInfoUpdateSource.REPAIR,
                )
            ) {
                is Success -> logcat { "Sync-UnifiedDevices: device_info re-published" }
                is Error -> {
                    logcat(WARN) { "Sync-UnifiedDevices: device_info re-publish failed: ${result.reason}" }
                    // release the slot so the next device list load can try again, without clobbering a claim another account has since made
                    republishedDeviceInfoForUserId.compareAndSet(userId, null)
                }
            }
        }
    }

    /**
     * Best-effort add of a missing ddg wrap after a list read that could not unwrap account_info for this credential.
     * Native (ddg) devices only: a 3party device already holds the wrap it needs. Failure never changes the list
     * being built; the per-account latch is released so the next load can retry without clobbering another account.
     */
    private fun repairAccountInfoDdgWrap() {
        if ((syncStore.credentialId ?: CREDENTIAL_ID_DDG) != CREDENTIAL_ID_DDG) return
        val userId = syncStore.userId ?: return
        val kid = syncStore.accountInfoPublicKey?.keyId ?: return
        if (repairedAccountInfoDdgWrapForUserId.getAndSet(userId) == userId) return

        appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat { "Sync-UnifiedDevices: ddg account_info wrap unavailable on read; repairing it" }
            when (val result = accountInfoDdgWrapRepairer.repair(kid)) {
                is Success -> logcat { "Sync-UnifiedDevices: ddg account_info wrap repaired" }
                is Error -> {
                    logcat(WARN) { "Sync-UnifiedDevices: ddg account_info wrap repair failed: ${result.reason}" }
                    repairedAccountInfoDdgWrapForUserId.compareAndSet(userId, null)
                }
            }
        }
    }

    private fun logoutFailedV2Devices(deviceIds: List<String>) {
        val thisDeviceId = syncStore.deviceId
        deviceIds.forEach { id ->
            if (id != thisDeviceId) remoteDeviceLogout.logout(id)
        }
    }

    private fun getConnectedDevicesLegacy(
        token: String,
        primaryKey: String,
    ): Result<List<ConnectedDevice>> = when (val result = syncApi.getDevices(token)) {
        is Error -> result.alsoFireAccountErrorPixel()
        is Success -> finishWith(decryptLegacyEntries(result.data.entries, primaryKey))
    }

    private fun decryptLegacyEntries(
        devices: List<Device>,
        primaryKey: String,
    ): List<ConnectedDevice> = devices.mapNotNull { device ->
        try {
            val decryptedDeviceName = nativeLib.decryptData(device.deviceName, primaryKey).decryptedData
            val decryptedDeviceType = device.deviceType.takeUnless { it.isNullOrEmpty() }?.let { encryptedDeviceType ->
                DeviceType(nativeLib.decryptData(encryptedDeviceType, primaryKey).decryptedData)
            } ?: DeviceType()

            ConnectedDevice(
                thisDevice = syncStore.deviceId == device.deviceId,
                deviceName = decryptedDeviceName,
                deviceId = device.deviceId,
                deviceType = decryptedDeviceType,
            )
        } catch (throwable: Throwable) {
            throwable.asErrorResult().alsoFireAccountErrorPixel()
            if (syncStore.deviceId != device.deviceId) {
                remoteDeviceLogout.logout(device.deviceId)
            }
            null
        }
    }

    private fun DecryptedDevice.toConnectedDevice(): ConnectedDevice = ConnectedDevice(
        thisDevice = syncStore.deviceId == deviceId,
        deviceName = name,
        deviceId = deviceId,
        deviceType = type?.takeUnless { it.isEmpty() }?.let { DeviceType(it) } ?: DeviceType(),
    )

    private fun finishWith(devices: List<ConnectedDevice>): Result<List<ConnectedDevice>> = Success(devices.sortedByDescending { it.thisDevice })

    private fun Error.alsoFireAccountErrorPixel(): Error {
        syncPixels.fireSyncAccountErrorPixel(this, SyncAccountOperation.GENERIC)
        return this
    }
}
