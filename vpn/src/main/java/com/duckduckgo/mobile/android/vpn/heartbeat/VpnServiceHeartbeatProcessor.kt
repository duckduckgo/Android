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
import android.content.Intent
import android.os.Build
import androidx.annotation.WorkerThread
import com.duckduckgo.mobile.android.vpn.dao.VpnPhoenixEntity
import com.duckduckgo.mobile.android.vpn.heartbeat.HeartBeatUtils.Companion.getAppExitReason
import com.duckduckgo.mobile.android.vpn.heartbeat.VpnHeartbeatReceiverService.Companion.EXTRA_HEART_BEAT_TYPE_ALIVE
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class VpnServiceHeartbeatProcessor @Inject constructor(
    private val context: Context,
    private val vpnDatabase: VpnDatabase
) {
    interface Listener {
        fun onStopReceived()

        fun onAliveReceived()

        fun onAliveMissed()
    }

    private fun Intent.getHeartbeatType(): String {
        return getStringExtra(VpnHeartbeatReceiverService.EXTRA_HEART_BEAT_TYPE).orEmpty()
    }

    private fun Intent.getHeartbeatValidityWindow(): Long {
        return getLongExtra(VpnHeartbeatReceiverService.EXTRA_VALID_PERIOD_SEC, -1)
    }

    @WorkerThread
    fun checkLastHeartBeat(listener: Listener) {
        val lastHeartBeat = vpnDatabase.vpnHeartBeatDao().hearBeats().maxByOrNull { it.timestamp }
        if (lastHeartBeat?.isAlive() == true) listener.onAliveMissed()
    }

    @WorkerThread
    fun processHeartBeat(intent: Intent, listener: Listener) {

        val type = intent.getHeartbeatType()

        val storedTimestamp = vpnDatabase.vpnHeartBeatDao().insertType(type).timestamp

        if (EXTRA_HEART_BEAT_TYPE_ALIVE != type) {
            listener.onStopReceived()
            return
        }

        listener.onAliveReceived()

        val validityWindowSeconds = intent.getHeartbeatValidityWindow()

        // we don't want to block the receiver so that it can continue receiving HBs
        GlobalScope.launch {
            if (validityWindowSeconds > 0) {
                delay(TimeUnit.SECONDS.toMillis((validityWindowSeconds * TIMEOUT_MULTIPLIER).toLong()))
                val lastHeartBeat = vpnDatabase.vpnHeartBeatDao().hearBeats().maxByOrNull { it.timestamp }

                if (lastHeartBeat?.isAlive() == true && storedTimestamp == lastHeartBeat.timestamp) {
                    listener.onAliveMissed()
                }
            }
        }
    }

    @WorkerThread
    fun restartVpnService() {
        vpnDatabase.vpnPhoenixDao().insert(VpnPhoenixEntity(reason = getAppExitReason(context)))
        TrackerBlockingVpnService.startIntent(context).also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(it)
            } else {
                context.startService(it)
            }
        }
    }

    companion object {
        private const val TIMEOUT_MULTIPLIER = 2.5
    }
}
