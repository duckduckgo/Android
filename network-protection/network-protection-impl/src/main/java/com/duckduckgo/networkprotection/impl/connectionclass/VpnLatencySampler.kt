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

import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.asServerDetails
import com.duckduckgo.networkprotection.impl.connectionclass.ConnectionQuality.EXCELLENT
import com.duckduckgo.networkprotection.impl.connectionclass.ConnectionQuality.GOOD
import com.duckduckgo.networkprotection.impl.connectionclass.ConnectionQuality.MODERATE
import com.duckduckgo.networkprotection.impl.connectionclass.ConnectionQuality.POOR
import com.duckduckgo.networkprotection.impl.connectionclass.ConnectionQuality.TERRIBLE
import com.duckduckgo.networkprotection.impl.connectionclass.ConnectionQuality.UNKNOWN
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.*
import logcat.logcat
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@ContributesMultibinding(VpnScope::class)
class VpnLatencySampler @Inject constructor(
    private val connectionClassManager: ConnectionClassManager,
    private val latencyMeasurer: LatencyMeasurer,
    private val wgTunnelConfig: WgTunnelConfig,
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionPixels: NetworkProtectionPixels,
) : VpnServiceCallbacks {

    private val job = ConflatedJob()
    private val latencyLastReported = AtomicLong(0)
    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        fun ConnectionQuality.reportLatency() {
            when (this) {
                TERRIBLE -> networkProtectionPixels.reportTerribleLatency()
                POOR -> networkProtectionPixels.reportPoorLatency()
                MODERATE -> networkProtectionPixels.reportModerateLatency()
                GOOD -> networkProtectionPixels.reportGoodLatency()
                EXCELLENT -> networkProtectionPixels.reportExcellentLatency()
                UNKNOWN -> { /** noop */ }
            }
        }

        job += coroutineScope.launch(dispatcherProvider.io()) {
            connectionClassManager.reset()

            while (isActive) {
                delay(5_000)

                // We do it like this instead of cancelling the jog whenever NetP is disabled to not mess with the
                // latency measurements
                if (networkProtectionState.isEnabled()) {
                    sampleLatency()?.let { quality ->
                        val nowSeconds = Instant.now().epochSecond
                        val diff = nowSeconds - latencyLastReported.getAndSet(nowSeconds)
                        if (diff.seconds.inWholeMinutes > 10) {
                            quality.reportLatency()
                        }
                    }
                }
            }
        }
    }

    private suspend fun sampleLatency(): ConnectionQuality? = withContext(dispatcherProvider.io()) {
        return@withContext wgTunnelConfig.getWgConfig()?.asServerDetails()?.ipAddress?.let { server ->
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
