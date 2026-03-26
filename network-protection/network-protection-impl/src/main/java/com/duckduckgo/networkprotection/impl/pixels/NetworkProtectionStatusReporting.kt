/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.pixels

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Module
@ContributesTo(AppScope::class)
object NetworkProtectionStatusReportingModule {
    @Provides
    @IntoSet
    fun provideNetworkProtectionStatusReporting(workManager: WorkManager): MainProcessLifecycleObserver {
        return NetworkProtectionStatusReporting(workManager)
    }
}

class NetworkProtectionStatusReporting(
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        scheduleNetworkProtectionStatusReporting()
    }

    private fun scheduleNetworkProtectionStatusReporting() {
        logcat { "Scheduling the NetworkProtectionStatusReporting worker" }

        PeriodicWorkRequestBuilder<NetworkProtectionStatusReportingWorker>(2, TimeUnit.HOURS)
            .addTag(WORKER_STATUS_REPORTING_TAG_V2)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build().run { workManager.enqueueUniquePeriodicWork(WORKER_STATUS_REPORTING_TAG_V2, ExistingPeriodicWorkPolicy.KEEP, this) }
    }

    companion object {
        private const val WORKER_STATUS_REPORTING_TAG_V2 = "WORKER_STATUS_REPORTING_TAG_V2"
    }
}

@ContributesWorker(AppScope::class)
class NetworkProtectionStatusReportingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var netpPixels: NetworkProtectionPixels

    @Inject
    lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    override suspend fun doWork(): Result {
        if (vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) {
            netpPixels.reportEnabled()
        } else {
            netpPixels.reportDisabled()
        }

        return Result.success()
    }
}
