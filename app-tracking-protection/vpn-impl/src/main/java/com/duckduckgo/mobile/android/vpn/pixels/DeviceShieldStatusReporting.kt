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
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.dao.VpnServiceStateStatsDao
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Module
@ContributesTo(AppScope::class)
object DeviceShieldStatusReportingModule {
    @Provides
    @IntoSet
    fun provideDeviceShieldStatusReporting(workManager: WorkManager): MainProcessLifecycleObserver {
        return DeviceShieldStatusReporting(workManager)
    }

    @Provides
    fun provideVpnServiceStateStatsDao(vpnDatabase: VpnDatabase): VpnServiceStateStatsDao {
        return vpnDatabase.vpnServiceStateDao()
    }
}

class DeviceShieldStatusReporting(
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        scheduleDeviceShieldStatusReporting()
    }

    private fun scheduleDeviceShieldStatusReporting() {
        logcat { "Scheduling the DeviceShieldStatusReporting worker" }

        PeriodicWorkRequestBuilder<DeviceShieldStatusReportingWorker>(24, TimeUnit.HOURS)
            .addTag(WORKER_STATUS_REPORTING_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build().run { workManager.enqueueUniquePeriodicWork(WORKER_STATUS_REPORTING_TAG, ExistingPeriodicWorkPolicy.KEEP, this) }
    }

    companion object {
        private const val WORKER_STATUS_REPORTING_TAG = "WORKER_STATUS_REPORTING_TAG"
    }
}

@ContributesWorker(AppScope::class)
class DeviceShieldStatusReportingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    override suspend fun doWork(): Result {
        if (vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)) {
            deviceShieldPixels.reportEnabled()
        } else {
            deviceShieldPixels.reportDisabled()
        }

        return Result.success()
    }
}
