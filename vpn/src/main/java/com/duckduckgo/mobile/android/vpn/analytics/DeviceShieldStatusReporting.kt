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

package com.duckduckgo.mobile.android.vpn.analytics

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.*
import com.duckduckgo.app.global.plugins.app.AppLifecycleObserverPlugin
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Module
@ContributesTo(AppObjectGraph::class)
class DeviceShieldStatusReportingModule {
    @Provides
    @IntoSet
    fun provideDeviceShieldStatusReporting(workManager: WorkManager): AppLifecycleObserverPlugin {
        return DeviceShieldStatusReporting(workManager)
    }

    @Provides
    @IntoSet
    fun provideDeviceShieldStatusReportingWorkerInjectorPlugin(
        deviceShieldAnalytics: DeviceShieldAnalytics
    ): WorkerInjectorPlugin {
        return DeviceShieldStatusReportingWorkerInjectorPlugin(deviceShieldAnalytics)
    }
}

class DeviceShieldStatusReporting(
    private val workManager: WorkManager
) : AppLifecycleObserverPlugin {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun scheduleDeviceShieldStatusReporting() {
        Timber.v("Scheduring the DeviceShieldStatusReporting workder")
        workManager.cancelAllWorkByTag(WORKER_STATUS_REPORTING_TAG)

        PeriodicWorkRequestBuilder<DeviceShieldStatusReportingWorker>(12, TimeUnit.HOURS)
            .addTag(WORKER_STATUS_REPORTING_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build().run { workManager.enqueue(this) }
    }

    class DeviceShieldStatusReportingWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        lateinit var deviceShieldAnalytics: DeviceShieldAnalytics

        override suspend fun doWork(): Result {
            if (TrackerBlockingVpnService.isServiceRunning(context)) {
                deviceShieldAnalytics.reportEnabled()
            } else {
                deviceShieldAnalytics.reportDisabled()
            }

            return Result.success()
        }
    }

    companion object {
        private const val WORKER_STATUS_REPORTING_TAG = "WORKER_STATUS_REPORTING_TAG"
    }
}

class DeviceShieldStatusReportingWorkerInjectorPlugin(private val deviceShieldAnalytics: DeviceShieldAnalytics) : WorkerInjectorPlugin {
    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is DeviceShieldStatusReporting.DeviceShieldStatusReportingWorker) {
            worker.deviceShieldAnalytics = deviceShieldAnalytics
            return true
        }
        return false
    }
}
