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

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Process
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import dagger.android.AndroidInjection
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Suppress("DEPRECATION")
class VpnHeartbeatReceiverService : IntentService("VpnHeartbeatReceiverService"), VpnServiceHeartbeatProcessor.Listener {

    @Inject
    lateinit var vpnDatabase: VpnDatabase

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var heartbeatProcessor: VpnServiceHeartbeatProcessor

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            Timber.e("Heartbeat service received null intent")
            return
        }
        when {
            intent.isActionBootCompleted() -> heartbeatProcessor.checkLastHeartBeat(this)
            intent.isActionHeartbeatReceived() -> heartbeatProcessor.processHeartBeat(intent, this)
            else -> Timber.w("(${Process.myPid()}) VPN heartbeat received wrong action = ${intent.action}")
        }
    }

    private fun Intent.isActionBootCompleted() : Boolean {
        return action == "android.intent.action.BOOT_COMPLETED"
    }

    private fun Intent.isActionHeartbeatReceived() : Boolean {
        return action == ACTION_VPN_HEART_BEAT
    }

    override fun onStopReceived() {
        Timber.v("(${Process.myPid()}) heartbeat STOP received")
    }

    override fun onAliveReceived() {
        Timber.v("(${Process.myPid()}) heartbeat ALIVE")
    }

    override fun onAliveMissed() {
        if (!TrackerBlockingVpnService.isServiceRunning(this)) {
            Timber.e("(${Process.myPid()}) heartbeat ALIVE missed - re-launcing VPN")
            deviceShieldPixels.suddenKillBySystem()
            deviceShieldPixels.automaticRestart()
            heartbeatProcessor.restartVpnService()
        } else {
            Timber.d("(${Process.myPid()}) heartbeat ALIVE missed, VPN still up - false positive️")
        }
    }

    companion object {

        const val ACTION_VPN_HEART_BEAT = "com.duckduckgo.mobile.android.vpn.heartbeat"
        const val EXTRA_VALID_PERIOD_SEC = "com.duckduckgo.mobile.android.vpn.heartbeat.VALID_PERIOD_SECONDS"
        const val EXTRA_HEART_BEAT_TYPE = "com.duckduckgo.mobile.android.vpn.heartbeat.TYPE"
        const val EXTRA_HEART_BEAT_TYPE_ALIVE = "ALIVE"
        const val EXTRA_HEART_BEAT_TYPE_STOPPED = "STOPPED"

        fun aliveServiceIntent(context: Context, validityWindow: Long, unit: TimeUnit): Intent {
            require(validityWindow > 0) { "validityWindow < 0: $validityWindow" }

            return serviceIntent(context).apply {
                putExtra(EXTRA_HEART_BEAT_TYPE, EXTRA_HEART_BEAT_TYPE_ALIVE)
                putExtra(EXTRA_VALID_PERIOD_SEC, unit.toSeconds(validityWindow))
            }
        }

        fun stoppedServiceIntent(context: Context): Intent {
            return serviceIntent(context).apply {
                putExtra(EXTRA_HEART_BEAT_TYPE, EXTRA_HEART_BEAT_TYPE_STOPPED)
            }
        }

        private fun serviceIntent(context: Context): Intent {
            return Intent(context, VpnHeartbeatReceiverService::class.java).apply {
                action = ACTION_VPN_HEART_BEAT
            }
        }
    }
}