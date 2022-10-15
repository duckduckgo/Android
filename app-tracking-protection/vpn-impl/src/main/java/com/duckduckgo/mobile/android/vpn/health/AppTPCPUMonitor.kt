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

package com.duckduckgo.mobile.android.vpn.health

import androidx.annotation.VisibleForTesting
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class AppTPCPUMonitor @Inject constructor(
    private val workManager: WorkManager,
    private val appTpFeatureConfig: AppTpFeatureConfig,
) : VpnServiceCallbacks {

    companion object {
        @VisibleForTesting
        const val APP_TRACKER_CPU_MONITOR_WORKER_TAG = "APP_TRACKER_CPU_MONITOR_WORKER_TAG"
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        if (appTpFeatureConfig.isEnabled(AppTpSetting.CPUMonitoring)) {
            Timber.d("AppTpSetting.CPUMonitoring is enabled, starting monitoring")
            val work = PeriodicWorkRequestBuilder<CPUMonitorWorker>(4, TimeUnit.HOURS)
                .setInitialDelay(10, TimeUnit.MINUTES) // let the CPU usage settle after VPN restart
                .build()

            workManager.enqueueUniquePeriodicWork(APP_TRACKER_CPU_MONITOR_WORKER_TAG, ExistingPeriodicWorkPolicy.KEEP, work)
        } else {
            Timber.d("AppTpSetting.CPUMonitoring is disabled")
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        Timber.v("AppTpSetting.CPUMonitoring - stopping")
        workManager.cancelUniqueWork(APP_TRACKER_CPU_MONITOR_WORKER_TAG)
    }
}
