/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.heartbeat

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.mobile.android.vpn.dao.VpnPhoenixEntity
import com.duckduckgo.mobile.android.vpn.heartbeat.HeartBeatUtils.Companion.getAppExitReason
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class VpnServiceHeartbeatProcessor @Inject constructor(
    private val context: Context,
    private val vpnDatabase: VpnDatabase,
    private val dispatcherProvider: DispatcherProvider
) {

    suspend fun didReceivedAliveLastTime(): Boolean = withContext(dispatcherProvider.io()) {
        val lastHeartBeat = vpnDatabase.vpnHeartBeatDao().hearBeats().maxByOrNull { it.timestamp }
        return@withContext lastHeartBeat?.isAlive() ?: false
    }

    suspend fun restartVpnService() = withContext(dispatcherProvider.io()) {
        vpnDatabase.vpnPhoenixDao().insert(VpnPhoenixEntity(reason = getAppExitReason(context)))
        TrackerBlockingVpnService.startIntent(context).also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(it)
            } else {
                context.startService(it)
            }
        }
    }

    suspend fun onStopReceive() = withContext(dispatcherProvider.io()) {
        vpnDatabase.vpnHeartBeatDao().insertType(VpnHeartbeatReceiverWorker.DATA_HEART_BEAT_TYPE_STOPPED)
        Timber.v("(${Process.myPid()}) heartbeat STOP received")
    }

    @WorkerThread
    suspend fun onAliveReceivedDidNextOneArrived(validityWindowSeconds: Long): Boolean = withContext(dispatcherProvider.io()) {

        val storedTimestamp = vpnDatabase.vpnHeartBeatDao().insertType(VpnHeartbeatReceiverWorker.DATA_HEART_BEAT_TYPE_ALIVE).timestamp

        Timber.v("(${Process.myPid()}) heartbeat ALIVE")

        if (validityWindowSeconds > 0) {
            delay(TimeUnit.SECONDS.toMillis((validityWindowSeconds * TIMEOUT_MULTIPLIER).toLong()))
            val lastHeartBeat = vpnDatabase.vpnHeartBeatDao().hearBeats().maxByOrNull { it.timestamp }

            return@withContext lastHeartBeat?.isAlive() == true && storedTimestamp == lastHeartBeat.timestamp
        }

        return@withContext false
    }

    companion object {
        private const val TIMEOUT_MULTIPLIER = 2.5
    }
}
