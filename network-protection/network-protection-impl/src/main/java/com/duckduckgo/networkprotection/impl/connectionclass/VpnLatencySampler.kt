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

package com.duckduckgo.networkprotection.impl.connectionclass

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.metrics.LatencyMeasurer
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.*
import logcat.logcat

@ContributesMultibinding(VpnScope::class)
class VpnLatencySampler @Inject constructor(
    private val connectionClassManager: ConnectionClassManager,
    private val latencyMeasurer: LatencyMeasurer,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionPixels: NetworkProtectionPixels,
) : VpnServiceCallbacks {

    private val job = ConflatedJob()
    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            connectionClassManager.reset()

            while (isActive) {
                delay(5_000)

                // We do it like this instead of cancelling the jog whenever NetP is disabled to not mess with the
                // latency measurements
                if (networkProtectionState.isEnabled()) {
                    sampleLatency()?.let { quality ->
                        when (quality) {
                            ConnectionQuality.TERRIBLE, ConnectionQuality.POOR -> {
                                logcat { "Connection Quality reporting POOR latency" }
                                networkProtectionPixels.reportPoorLatency()
                            }

                            else -> {
                                // noop
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun sampleLatency(): ConnectionQuality? = withContext(dispatcherProvider.io()) {
        return@withContext networkProtectionRepository.serverDetails?.ipAddress?.let { server ->
            val latency = latencyMeasurer.measureLatency(server)
            if (latency >= 0) {
                connectionClassManager.addLatency(latency.toDouble())
            } else {
                // < 0 latency signals an error and we don't add measurement errors but rather report them
                networkProtectionPixels.reportLatencyMeasurementError()
            }
            val quality = connectionClassManager.getConnectionQuality()
            val latencyRawAverage = connectionClassManager.getLatencyAverage()
            logcat { "Connection Quality ($server) = $quality / current = $latency / avg = $latencyRawAverage" }

            // return
            quality
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        job.cancel()
    }
}
