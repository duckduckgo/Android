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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.*
import com.duckduckgo.app.global.plugins.app.AppLifecycleObserverPlugin
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.analytics.DeviceShieldAnalytics
import com.duckduckgo.mobile.android.vpn.dao.VpnHeartBeatDao
import com.duckduckgo.mobile.android.vpn.dao.VpnPhoenixDao
import com.duckduckgo.mobile.android.vpn.dao.VpnPhoenixEntity
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Module
@ContributesTo(AppObjectGraph::class)
class VpnServiceHeartbeatMonitorModule {
    @Provides
    @IntoSet
    fun provideVpnServiceHeartbeatMonitor(workManager: WorkManager): AppLifecycleObserverPlugin {
        return VpnServiceHeartbeatMonitor(workManager)
    }

    @Provides
    @IntoSet
    fun provideVpnServiceHeartbeatMonitorWorkerInjectorPlugin(
        deviceShieldAnalytics: DeviceShieldAnalytics,
        vpnDatabase: VpnDatabase
    ): WorkerInjectorPlugin {
        return VpnServiceHeartbeatMonitorWorkerInjectorPlugin(deviceShieldAnalytics, vpnDatabase)
    }
}

class VpnServiceHeartbeatMonitor(
    private val workManager: WorkManager
) : AppLifecycleObserverPlugin {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun startHearbeatMonitor() {
        Timber.v("(Re)Scheduling the VpnServiceHeartbeatMonitor worker")
        workManager.cancelAllWorkByTag(WORKER_HEART_BEAT_MONITOR_TAG)

        val request = PeriodicWorkRequestBuilder<VpnServiceHeartbeatMonitorWorker>(15, TimeUnit.MINUTES)
            .addTag(WORKER_HEART_BEAT_MONITOR_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()

        workManager.enqueue(request)
    }

    class VpnServiceHeartbeatMonitorWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        lateinit var vpnPhoenixDao: VpnPhoenixDao
        lateinit var vpnHeartBeatDao: VpnHeartBeatDao
        lateinit var deviceShieldAnalytics: DeviceShieldAnalytics

        override suspend fun doWork(): Result {
            val lastHeartBeat = vpnHeartBeatDao.hearBeats().maxByOrNull { it.timestamp }

            if (lastHeartBeat?.isAlive() == true && !TrackerBlockingVpnService.isServiceRunning(context)) {
                Timber.w("VPN stopped, restarting it")

                vpnPhoenixDao.insert(VpnPhoenixEntity(reason = HeartBeatUtils.getAppExitReason(context)))

                TrackerBlockingVpnService.startIntent(context).also {
                    deviceShieldAnalytics.suddenKillBySystem()
                    deviceShieldAnalytics.automaticRestart()

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(it)
                    } else {
                        context.startService(it)
                    }
                }
            }

            return Result.success()
        }
    }

    companion object {
        private const val WORKER_HEART_BEAT_MONITOR_TAG = "VpnServiceHeartbeatMonitorWorker"
    }
}

class VpnServiceHeartbeatMonitorWorkerInjectorPlugin(
    private val deviceShieldAnalytics: DeviceShieldAnalytics,
    private val vpnDatabase: VpnDatabase
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is VpnServiceHeartbeatMonitor.VpnServiceHeartbeatMonitorWorker) {
            worker.vpnHeartBeatDao = vpnDatabase.vpnHeartBeatDao()
            worker.vpnPhoenixDao = vpnDatabase.vpnPhoenixDao()
            worker.deviceShieldAnalytics = deviceShieldAnalytics

            return true
        }

        return false
    }
}
