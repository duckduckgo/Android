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

package com.duckduckgo.networkprotection.impl.rekey

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit.HOURS
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import logcat.logcat

@ContributesMultibinding(VpnScope::class)
class NetPRekeyScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
) : VpnServiceCallbacks {
    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        if (runBlocking { vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN) }) {
            logcat { "NetPRekeyScheduler onVpnStarted called with NetP enabled" }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresDeviceIdle(true)
                .build()
            val workerRequest = PeriodicWorkRequestBuilder<NetPRekeyWorker>(24, HOURS)
                .addTag(DAILY_NETP_REKEY_TAG)
                .setInitialDelay(24, HOURS)
                .setConstraints(constraints)
                .build()
            // Once work is already scheduled, we don't update the interval
            // since onVpnStarted will be called many times when restarting/reconnecting the VPNService.
            workManager.enqueueUniquePeriodicWork(DAILY_NETP_REKEY_TAG, ExistingPeriodicWorkPolicy.KEEP, workerRequest)
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        // DO NOTHING
    }

    companion object {
        internal const val DAILY_NETP_REKEY_TAG = "DAILY_NETP_REKEY_TAG"
    }
}
