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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class VpnHeartbeatReceiverWorker(context: Context, workerParameters: WorkerParameters) : CoroutineWorker(context, workerParameters) {
    lateinit var heartbeatProcessor: VpnServiceHeartbeatProcessor
    lateinit var deviceShieldPixels: DeviceShieldPixels

    override suspend fun doWork(): Result {
        when {
            inputData.isActionBootCompleted() && heartbeatProcessor.didReceivedAliveLastTime() -> restartVpnService()
            inputData.isHeartbeatTypeStopped() -> heartbeatProcessor.onStopReceive()
            inputData.isHeartbeatTypeAlive() && heartbeatProcessor.onAliveReceivedDidNextOneArrived(inputData.getHeartbeatValidityWindow()) -> restartVpnService()
            else -> Timber.v("(${Process.myPid()}) VPN heartbeat received, noop. Data = $inputData")
        }

        return Result.success()
    }

    private suspend fun restartVpnService() {
        if (!TrackerBlockingVpnService.isServiceRunning(applicationContext)) {
            Timber.e("(${Process.myPid()}) heartbeat ALIVE missed - re-launcing VPN")
            deviceShieldPixels.suddenKillBySystem()
            deviceShieldPixels.automaticRestart()
            heartbeatProcessor.restartVpnService()
        } else {
            Timber.d("(${Process.myPid()}) heartbeat ALIVE missed, VPN still up - false positive️")
        }
    }

    private fun Data.isActionBootCompleted(): Boolean {
        return getString(DATA_HEART_BEAT_TYPE_KEY).equals(DATA_HEART_BEAT_TYPE_BOOT_COMPLETED)
    }

    private fun Data.isHeartbeatTypeStopped(): Boolean {
        return getString(DATA_HEART_BEAT_TYPE_KEY).equals(DATA_HEART_BEAT_TYPE_STOPPED)
    }

    private fun Data.isHeartbeatTypeAlive(): Boolean {
        return getString(DATA_HEART_BEAT_TYPE_KEY).equals(DATA_HEART_BEAT_TYPE_ALIVE)
    }

    private fun Data.getHeartbeatValidityWindow(): Long {
        return getLong(DATA_VALID_PERIOD_SEC_KEY, -1)
    }

    companion object {
        private const val DATA_VALID_PERIOD_SEC_KEY = "com.duckduckgo.mobile.android.vpn.heartbeat.VALID_PERIOD_SECONDS"
        private const val DATA_HEART_BEAT_TYPE_KEY = "com.duckduckgo.mobile.android.vpn.heartbeat.TYPE"
        private const val DATA_HEART_BEAT_TYPE_BOOT_COMPLETED = "BOOT_COMPLETED"
        const val DATA_HEART_BEAT_TYPE_ALIVE = "ALIVE"
        const val DATA_HEART_BEAT_TYPE_STOPPED = "STOPPED"

        private const val HEARTBEAT_RECEIVER_WORKER_TAG = "HEARTBEAT_RECEIVER_WORKER_TAG"

        fun sendAliveHeartbeat(workerManager: WorkManager, validityWindowSeconds: Long) {
            Timber.d("Sending ALIVE heartbeat")
            val workRequest = OneTimeWorkRequestBuilder<VpnHeartbeatReceiverWorker>()
                .addTag(HEARTBEAT_RECEIVER_WORKER_TAG)
                .setInputData(
                    Data.Builder()
                        .putString(DATA_HEART_BEAT_TYPE_KEY, DATA_HEART_BEAT_TYPE_ALIVE)
                        .putLong(DATA_VALID_PERIOD_SEC_KEY, validityWindowSeconds)
                        .build()
                )
                .build()
            workerManager.enqueue(workRequest)
        }

        fun sendStopHeartbeat(workerManager: WorkManager) {
            Timber.d("Sending STOPPED heartbeat")
            val workRequest = OneTimeWorkRequestBuilder<VpnHeartbeatReceiverWorker>()
                .addTag(HEARTBEAT_RECEIVER_WORKER_TAG)
                .setInputData(
                    Data.Builder()
                        .putString(DATA_HEART_BEAT_TYPE_KEY, DATA_HEART_BEAT_TYPE_STOPPED)
                        .build()
                )
                .build()
            workerManager.enqueue(workRequest)
        }

        fun sendDeviceBootCheck(workerManager: WorkManager) {
            Timber.d("Sending BOOT_COMPLETED heartbeat")
            val workRequest = OneTimeWorkRequestBuilder<VpnHeartbeatReceiverWorker>()
                .addTag(HEARTBEAT_RECEIVER_WORKER_TAG)
                .setInputData(
                    Data.Builder()
                        .putString(DATA_HEART_BEAT_TYPE_KEY, DATA_HEART_BEAT_TYPE_BOOT_COMPLETED)
                        .build()
                )
                .build()
            workerManager.enqueue(workRequest)
        }
    }
}

class VpnHeartbeatDeviceBootMonitor : BroadcastReceiver() {
    @Inject lateinit var workManager: WorkManager

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            Timber.v("Checking if VPN was running before device BOOT")

            VpnHeartbeatReceiverWorker.sendDeviceBootCheck(workManager)
        }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class VpnHeartbeatReceiverWorkerInjectorPlugin @Inject constructor(
    private val vpnServiceHeartbeatProcessor: VpnServiceHeartbeatProcessor,
    private val deviceShieldPixels: DeviceShieldPixels
) : WorkerInjectorPlugin {
    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is VpnHeartbeatReceiverWorker) {
            worker.heartbeatProcessor = vpnServiceHeartbeatProcessor
            worker.deviceShieldPixels = deviceShieldPixels
            return true
        }
        return false
    }
}
