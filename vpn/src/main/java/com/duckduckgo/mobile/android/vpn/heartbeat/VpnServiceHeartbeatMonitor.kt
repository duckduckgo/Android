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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.dao.HeartBeatEntity
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.dao.VpnHeartBeatDao
import com.duckduckgo.mobile.android.vpn.dao.VpnPhoenixDao
import com.duckduckgo.mobile.android.vpn.dao.VpnPhoenixEntity
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.AndroidInjection
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class VpnServiceHeartbeatMonitor(
    private val workManager: WorkManager
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun startHearbeatMonitor() {
        Companion.startHearbeatMonitor(workManager)
    }

    class VpnServiceHeartbeatMonitorWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        lateinit var vpnPhoenixDao: VpnPhoenixDao
        lateinit var vpnHeartBeatDao: VpnHeartBeatDao
        lateinit var deviceShieldPixels: DeviceShieldPixels

        override suspend fun doWork(): Result {
            val lastHeartBeat = vpnHeartBeatDao.hearBeats().maxByOrNull { it.timestamp }

            Timber.d("HB monitor checking last HB: $lastHeartBeat")
            if (lastHeartBeat?.isAlive() == true && !TrackerBlockingVpnService.isServiceRunning(context)) {
                Timber.w("HB monitor: VPN stopped, restarting it")

                vpnPhoenixDao.insert(VpnPhoenixEntity(reason = HeartBeatUtils.getAppExitReason(context)))

                deviceShieldPixels.suddenKillBySystem()
                deviceShieldPixels.automaticRestart()
                TrackerBlockingVpnService.startService(context)
            }

            return Result.success()
        }

        private fun HeartBeatEntity.isAlive(): Boolean {
            return DATA_HEART_BEAT_TYPE_ALIVE == type
        }
    }

    companion object {
        const val DATA_HEART_BEAT_TYPE_ALIVE = "ALIVE"
        const val DATA_HEART_BEAT_TYPE_STOPPED = "STOPPED"
        private const val WORKER_HEART_BEAT_MONITOR_TAG = "VpnServiceHeartbeatMonitorWorker"

        fun startHearbeatMonitor(workManager: WorkManager) {
            Timber.v("(Re)Scheduling the VpnServiceHeartbeatMonitor worker")
            workManager.cancelAllWorkByTag(WORKER_HEART_BEAT_MONITOR_TAG)

            val request = PeriodicWorkRequestBuilder<VpnServiceHeartbeatMonitorWorker>(15, TimeUnit.MINUTES)
                .addTag(WORKER_HEART_BEAT_MONITOR_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            workManager.enqueue(request)
        }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class VpnReminderNotificationWorkerInjectorPlugin @Inject constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val vpnDatabase: VpnDatabase
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is VpnServiceHeartbeatMonitor.VpnServiceHeartbeatMonitorWorker) {
            worker.vpnHeartBeatDao = vpnDatabase.vpnHeartBeatDao()
            worker.vpnPhoenixDao = vpnDatabase.vpnPhoenixDao()
            worker.deviceShieldPixels = deviceShieldPixels

            return true
        }

        return false
    }
}

class VpnHeartbeatDeviceBootMonitor : BroadcastReceiver() {
    @Inject
    lateinit var workManager: WorkManager

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            Timber.v("Checking if VPN was running before device BOOT")

            VpnServiceHeartbeatMonitor.startHearbeatMonitor(workManager)
        }
    }
}
