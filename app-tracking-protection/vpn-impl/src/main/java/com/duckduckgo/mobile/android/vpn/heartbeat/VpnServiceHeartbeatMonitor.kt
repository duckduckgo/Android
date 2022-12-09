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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.mobile.android.vpn.dao.HeartBeatEntity
import com.duckduckgo.mobile.android.vpn.dao.VpnHeartBeatDao
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.android.AndroidInjection
import dagger.multibindings.IntoSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat

@Module
@ContributesTo(AppScope::class)
class VpnServiceHeartbeatMonitorModule {
    @Provides
    @IntoSet
    fun provideVpnServiceHeartbeatMonitor(workManager: WorkManager): LifecycleObserver {
        return VpnServiceHeartbeatMonitor(workManager)
    }

    @Provides
    fun providesVpnHeartBeatDao(vpnDatabase: VpnDatabase): VpnHeartBeatDao = vpnDatabase.vpnHeartBeatDao()
}

class VpnServiceHeartbeatMonitor(
    private val workManager: WorkManager,
) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        startHeartbeatMonitor(workManager)
    }

    companion object {
        const val DATA_HEART_BEAT_TYPE_ALIVE = "ALIVE"
        const val DATA_HEART_BEAT_TYPE_STOPPED = "STOPPED"
        private const val WORKER_HEART_BEAT_MONITOR_TAG = "VpnServiceHeartbeatMonitorWorker"

        fun startHeartbeatMonitor(workManager: WorkManager) {
            logcat { "(Re)Scheduling the VpnServiceHeartbeatMonitor worker" }
            workManager.cancelAllWorkByTag(WORKER_HEART_BEAT_MONITOR_TAG)

            val request = PeriodicWorkRequestBuilder<VpnServiceHeartbeatMonitorWorker>(15, TimeUnit.MINUTES)
                .addTag(WORKER_HEART_BEAT_MONITOR_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            workManager.enqueue(request)
        }
    }
}

@ContributesWorker(AppScope::class)
class VpnServiceHeartbeatMonitorWorker(
    val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var vpnHeartBeatDao: VpnHeartBeatDao

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var trackerBlockingStatsRepository: AppTrackerBlockingStatsRepository

    @Inject
    lateinit var appTpFeatureConfig: AppTpFeatureConfig

    override suspend fun doWork(): Result = withContext(dispatcherProvider.io()) {
        val lastHeartBeat = vpnHeartBeatDao.hearBeats().maxByOrNull { it.timestamp }

        logcat { "HB monitor checking last HB: $lastHeartBeat" }
        if (lastHeartBeat?.isAlive() == true && !TrackerBlockingVpnService.isServiceRunning(context)) {
            logcat(LogPriority.WARN) { "HB monitor: VPN stopped, restarting it" }

            deviceShieldPixels.suddenKillBySystem()
            deviceShieldPixels.automaticRestart()
            TrackerBlockingVpnService.startService(context)
        } else if (didNotBlockRecently()) {
            deviceShieldPixels.automaticRestart()
            // we have not blocked anything "recently", assuming something is wrong with the VPN service
            logcat { "HB monitor: VPN not blocking anything, restarting it" }
            TrackerBlockingVpnService.restartVpnService(context)
        }

        return@withContext Result.success()
    }

    private fun HeartBeatEntity.isAlive(): Boolean {
        return VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_ALIVE == type
    }

    private suspend fun didNotBlockRecently(): Boolean {
        fun isFeatureDisabled(): Boolean {
            return !appTpFeatureConfig.isEnabled(AppTpSetting.CheckBlockingFunction)
        }

        if (isFeatureDisabled()) return false

        val timeWindow = AppTrackerBlockingStatsRepository.TimeWindow(3, TimeUnit.HOURS)
        // if we ever blocked trackers, and we have not blocked anything in the last 3 hours, we assume something is wrong
        return trackerBlockingStatsRepository.containsVpnTrackers() &&
            trackerBlockingStatsRepository.getVpnTrackersSync({ timeWindow.asString() }).isEmpty()
    }
}

@InjectWith(ReceiverScope::class)
class VpnHeartbeatDeviceBootMonitor : BroadcastReceiver() {
    @Inject
    lateinit var workManager: WorkManager

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AndroidInjection.inject(this, context)

        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            logcat { "Checking if VPN was running before device BOOT" }

            VpnServiceHeartbeatMonitor.startHeartbeatMonitor(workManager)
        }
    }
}
