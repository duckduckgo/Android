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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * One-time, per-device backfill that brings an already signed-in user into the unified device list by writing this device's `device_info`.
 */
@WorkerThread
interface DeviceInfoMigrator {
    /**
     * Runs the migration if it hasn't been done for the current account and the write feature is
     * enabled. Concurrent calls collapse into a single run.
     *
     * @return [Success] when migration completed or there is nothing left to do
     * @return [Error] if it couldn't be migrated, leaving it so that it can be tried again
     */
    suspend fun ensureMigrated(): Result<Unit>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDeviceInfoMigrator @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncFeature: SyncFeature,
    private val syncDeviceIds: SyncDeviceIds,
    private val deviceInfoUpdater: DeviceInfoUpdater,
    private val dispatchers: DispatcherProvider,
) : DeviceInfoMigrator {

    private val mutex = Mutex()

    override suspend fun ensureMigrated(): Result<Unit> = withContext(dispatchers.io()) {
        mutex.withLock { migrate() }
    }

    private suspend fun migrate(): Result<Unit> {
        logcat { "Sync-UnifiedDevices: Checking if unified device list migration is needed" }

        val inputs = when (val precheck = checkPreconditions()) {
            is Precheck.Proceed -> precheck
            is Precheck.Stop -> return precheck.result
        }

        logcat { "Sync-UnifiedDevices: migration needed; checking server for existing device_info" }
        when (val serverHasDeviceInfoResult = serverAlreadyHasDeviceInfo(inputs.token)) {
            is Success -> if (serverHasDeviceInfoResult.data) {
                logcat { "Sync-UnifiedDevices: server already has device_info for this device; marking migration done" }
                markMigrated(inputs.userId)
                return Success(Unit)
            }
            is Error -> return serverHasDeviceInfoResult
        }

        logcat { "Sync-UnifiedDevices: no device_info on server for this device; writing device_info" }
        return writeDeviceInfo(inputs.userId)
    }

    private fun checkPreconditions(): Precheck {
        val userId = syncStore.userId.takeUnless { it.isNullOrEmpty() }
            ?: run {
                logcat { "Sync-UnifiedDevices: migration skipped — not signed in" }
                return Precheck.Stop(Error(reason = "DeviceInfoMigration: not signed in"))
            }

        if (!syncFeature.canWriteDeviceInfo()) {
            logcat { "Sync-UnifiedDevices: migration skipped — writing device_info is disabled" }
            return Precheck.Stop(Success(Unit))
        }

        if (syncStore.unifiedDeviceListMigratedForUserId == userId) {
            logcat { "Sync-UnifiedDevices: migration already done for this account; nothing to do" }
            return Precheck.Stop(Success(Unit))
        }

        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: run {
                logcat { "Sync-UnifiedDevices: migration skipped — no token" }
                return Precheck.Stop(Error(reason = "DeviceInfoMigration: no token"))
            }

        return Precheck.Proceed(userId = userId, token = token)
    }

    /** [Success] `true` if this device already has `device_info` on the server, `false` otherwise. */
    private fun serverAlreadyHasDeviceInfo(token: String): Result<Boolean> {
        return when (val devices = syncApi.getDevices(token)) {
            is Success -> {
                val thisDevice = devices.data.entriesV2?.firstOrNull { it.deviceId == syncStore.deviceId }
                Success(!thisDevice?.deviceInfo.isNullOrEmpty())
            }
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: migration getDevices failed, will retry later: ${devices.reason}" }
                devices
            }
        }
    }

    private suspend fun writeDeviceInfo(userId: String): Result<Unit> {
        return when (
            val updateResult = deviceInfoUpdater.setThisDeviceName(
                name = syncDeviceIds.deviceName(),
                source = DeviceInfoUpdateSource.FIRST_WRITE,
            )
        ) {
            is Success -> {
                markMigrated(userId)
                logcat { "Sync-UnifiedDevices: migration complete for this device (${updateResult.data.size} devices_v2 returned)" }
                Success(Unit)
            }
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: migration PATCH failed, will retry later: ${updateResult.reason}" }
                updateResult
            }
        }
    }

    private fun markMigrated(userId: String) {
        syncStore.unifiedDeviceListMigratedForUserId = userId
    }

    /** The signed-in inputs the migration needs, or a terminal [Result] to short-circuit on. */
    private sealed interface Precheck {
        data class Proceed(val userId: String, val token: String) : Precheck
        data class Stop(val result: Result<Unit>) : Precheck
    }
}
