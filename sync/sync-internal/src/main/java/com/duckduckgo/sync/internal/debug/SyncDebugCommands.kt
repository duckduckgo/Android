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

package com.duckduckgo.sync.internal.debug

import android.annotation.SuppressLint
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.engine.RealSyncScheduler
import com.duckduckgo.sync.impl.engine.SyncStateRepository
import com.duckduckgo.sync.impl.internal.SyncInternalEnvDataStore
import com.duckduckgo.sync.impl.triggers.SyncBackgroundWorker
import com.duckduckgo.sync.internal.ui.AuditLogCheckDebugControls
import com.duckduckgo.sync.internal.ui.AuditLogDebugReport
import com.duckduckgo.sync.internal.ui.OtherDevicesDebugTool
import com.duckduckgo.sync.store.SyncStore
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * Setup, teardown and read-only checks for driving Sync from adb (see [SyncDebugBroadcastReceiver]).
 * Nothing here stands in for a step a user takes: `runBackgroundSync` runs the real background worker.
 */
class SyncDebugCommands @Inject constructor(
    private val syncStore: SyncStore,
    private val syncFeature: SyncFeature,
    private val envDataStore: SyncInternalEnvDataStore,
    private val syncAccountRepository: SyncAccountRepository,
    private val otherDevicesDebugTool: OtherDevicesDebugTool,
    private val auditLogDebugReport: AuditLogDebugReport,
    private val auditLogCheckDebugControls: AuditLogCheckDebugControls,
    private val workManager: WorkManager,
    private val syncStateRepository: SyncStateRepository,
) {

    suspend fun run(command: String?, on: Boolean): String = when {
        command in STAGING_ONLY && !envDataStore.useSyncDevEnvironment -> "refused: $command only runs on staging; send $USE_STAGING first"
        else -> runCommand(command, on)
    }

    private suspend fun runCommand(command: String?, on: Boolean): String = when (command) {
        STATUS -> status()
        USE_STAGING -> useStaging(on)
        SET_FLAGS -> setFlags(on)
        SIMULATE_LOGIN -> otherDevicesDebugTool.simulateLogin()
        LOG_OUT_OTHERS -> logOutOthers()
        READ_LOG -> readLog()
        DELETE_ACCOUNT -> deleteAccount()
        RUN_BACKGROUND_SYNC -> runBackgroundSync()
        AGE_LAST_SYNC -> ageLastSync()
        else -> "unknown command \"$command\"; known: ${COMMANDS.joinToString()}"
    }

    private suspend fun status(): String =
        "signedIn=${syncStore.isSignedIn()} environment=${envDataStore.syncEnvironmentUrl} " +
            "loginAuditLog=${syncFeature.loginAuditLog().isEnabled()} " +
            "loginNotification=${syncFeature.loginNotification().isEnabled()} " +
            auditLogCheckDebugControls.unseenCount()

    private fun useStaging(on: Boolean): String {
        envDataStore.useSyncDevEnvironment = on
        return "environment=${envDataStore.syncEnvironmentUrl}"
    }

    @SuppressLint("DenyListedApi")
    private fun setFlags(on: Boolean): String {
        listOf(syncFeature.loginAuditLog(), syncFeature.loginNotification()).forEach { toggle ->
            val state = toggle.getRawStoredState()?.copy(enable = on, remoteEnableState = on)
                ?: Toggle.State(enable = on, remoteEnableState = on)
            toggle.setRawStoredState(state)
        }
        return "loginAuditLog=$on loginNotification=$on"
    }

    private fun logOutOthers(): String {
        val others = otherDevicesDebugTool.otherDevices()
        others.error?.let { return it }
        if (others.devices.isEmpty()) return "no other devices"
        return others.devices.joinToString("\n") { otherDevicesDebugTool.logOut(it) }
    }

    private suspend fun readLog(): String {
        val token = syncStore.token
        if (token.isNullOrEmpty()) return NOT_SIGNED_IN
        return auditLogDebugReport.fetchAndDescribe(token, syncStore.deviceId)
    }

    private fun deleteAccount(): String {
        if (!syncStore.isSignedIn()) return NOT_SIGNED_IN
        return when (val result = syncAccountRepository.deleteAccount()) {
            is Result.Success -> "account deleted"
            is Result.Error -> "delete failed: ${result.reason} (code ${result.code})"
        }
    }

    private fun runBackgroundSync(): String {
        if (!syncStore.isSignedIn()) return NOT_SIGNED_IN
        workManager.enqueue(OneTimeWorkRequestBuilder<SyncBackgroundWorker>().build())
        return "enqueued SyncBackgroundWorker"
    }

    private fun ageLastSync(): String {
        val last = syncStateRepository.current() ?: return "no sync attempt stored"
        val aged = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(RealSyncScheduler.DEBOUNCE_PERIOD_IN_MINUTES + 1L)
        syncStateRepository.store(last.copy(timestamp = DatabaseDateFormatter.iso8601(aged)))
        return "last sync (${last.state}) moved to ${DatabaseDateFormatter.iso8601(aged)}"
    }

    companion object {
        const val STATUS = "status"
        const val USE_STAGING = "useStaging"
        const val SET_FLAGS = "setFlags"
        const val SIMULATE_LOGIN = "simulateLogin"
        const val LOG_OUT_OTHERS = "logOutOthers"
        const val READ_LOG = "readLog"
        const val DELETE_ACCOUNT = "deleteAccount"
        const val RUN_BACKGROUND_SYNC = "runBackgroundSync"
        const val AGE_LAST_SYNC = "ageLastSync"
        val COMMANDS = listOf(
            STATUS,
            USE_STAGING,
            SET_FLAGS,
            SIMULATE_LOGIN,
            LOG_OUT_OTHERS,
            READ_LOG,
            DELETE_ACCOUNT,
            RUN_BACKGROUND_SYNC,
            AGE_LAST_SYNC,
        )
        val STAGING_ONLY = setOf(SIMULATE_LOGIN, LOG_OUT_OTHERS, DELETE_ACCOUNT)
        const val NOT_SIGNED_IN = "Not signed in"
    }
}
