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

package com.duckduckgo.networkprotection.impl.metrics

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat

@ContributesMultibinding(VpnScope::class)
class LatencyMonitorCallback @Inject constructor(
    private val workManager: WorkManager,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
) : VpnServiceCallbacks {

    companion object {
        const val NETP_LATENCY_MONITOR_WORKER_TAG = "NETP_LATENCY_MONITOR_WORKER_TAG"

        const val SERVER_IP_INPUT = "SERVER_IP"
        const val SERVER_NAME_INPUT = "SERVER_NAME"
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        if (runBlocking { !vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN) }) {
            logcat { "NetP not enabled, not starting latency monitor" }
            return
        }

        val serverIP = networkProtectionRepository.serverDetails?.ipAddress
        if (serverIP == null) {
            logcat(LogPriority.WARN) { "server details not available - not starting latency monitor" }
            return
        }

        val work = PeriodicWorkRequestBuilder<LatencyMonitorWorker>(4, TimeUnit.HOURS)
            .setInputData(
                workDataOf(
                    SERVER_IP_INPUT to serverIP,
                    SERVER_NAME_INPUT to networkProtectionRepository.serverDetails?.serverName,
                ),
            )
            .setInitialDelay(15, TimeUnit.MINUTES) // let the VPN settle a bit before measuring
            .build()

        // replace existing in case server IP Address changed
        workManager.enqueueUniquePeriodicWork(NETP_LATENCY_MONITOR_WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, work)
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        workManager.cancelUniqueWork(NETP_LATENCY_MONITOR_WORKER_TAG)
    }
}
