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

package com.duckduckgo.mobile.android.vpn.pixels

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.dao.VpnServiceStateStatsDao
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDateTime
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Module
@ContributesTo(AppScope::class)
class DeviceShieldStatusReportingModule {
    @Provides
    @IntoSet
    fun provideDeviceShieldStatusReporting(workManager: WorkManager): LifecycleObserver {
        return DeviceShieldStatusReporting(workManager)
    }

    @Provides
    @IntoSet
    fun provideDeviceShieldStatusReportingWorkerInjectorPlugin(
        deviceShieldPixels: DeviceShieldPixels,
        vpnDatabase: VpnDatabase
    ): WorkerInjectorPlugin {
        return DeviceShieldStatusReportingWorkerInjectorPlugin(deviceShieldPixels, vpnDatabase)
    }
}

class DeviceShieldStatusReporting(
    private val workManager: WorkManager
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun scheduleDeviceShieldStatusReporting() {
        Timber.v("Scheduling the DeviceShieldStatusReporting worker")
        workManager.cancelAllWorkByTag(WORKER_STATUS_REPORTING_TAG)

        PeriodicWorkRequestBuilder<DeviceShieldStatusReportingWorker>(12, TimeUnit.HOURS)
            .addTag(WORKER_STATUS_REPORTING_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build().run { workManager.enqueue(this) }
    }

    class DeviceShieldStatusReportingWorker(
        private val context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {
        lateinit var deviceShieldPixels: DeviceShieldPixels
        lateinit var vpnServiceStateStatsDao: VpnServiceStateStatsDao

        override suspend fun doWork(): Result {
            if (TrackerBlockingVpnService.isServiceRunning(context)) {
                deviceShieldPixels.reportEnabled()
            } else {
                deviceShieldPixels.reportDisabled()
            }

            sendLastDayVpnEnableDisableCounts()

            return Result.success()
        }

        private suspend fun sendLastDayVpnEnableDisableCounts() = withContext(Dispatchers.IO) {
            val startTime = LocalDateTime.now().minusDays(1).toLocalDate().atStartOfDay().run {
                DatabaseDateFormatter.timestamp(this)
            }

            val lastDayVpnStats = vpnServiceStateStatsDao.getServiceStateStatsSince(startTime)
                .groupBy { it.day }[startTime.substringBefore("T")]
                ?.groupBy { it.vpnServiceStateStats.state }

            lastDayVpnStats?.let { stats ->
                deviceShieldPixels.reportLastDayEnableCount(stats[VpnServiceState.ENABLED].orEmpty().size)
                deviceShieldPixels.reportLastDayDisableCount(stats[VpnServiceState.DISABLED].orEmpty().size)
            }
        }
    }

    companion object {
        private const val WORKER_STATUS_REPORTING_TAG = "WORKER_STATUS_REPORTING_TAG"
    }
}

class DeviceShieldStatusReportingWorkerInjectorPlugin(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val vpnDatabase: VpnDatabase
) : WorkerInjectorPlugin {
    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is DeviceShieldStatusReporting.DeviceShieldStatusReportingWorker) {
            worker.deviceShieldPixels = deviceShieldPixels
            worker.vpnServiceStateStatsDao = vpnDatabase.vpnServiceStateDao()
            return true
        }
        return false
    }
}
