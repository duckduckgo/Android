/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.failure

import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.CurrentTimeProvider
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.asServerDetails
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.pixels.WireguardHandshakeMonitor
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@ContributesMultibinding(VpnScope::class)
class FailureRecoveryHandler @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val wgTunnel: WgTunnel,
    private val wgTunnelConfig: WgTunnelConfig,
    private val currentTimeProvider: CurrentTimeProvider,
    private val networkProtectionPixels: NetworkProtectionPixels,
    private val dispatcherProvider: DispatcherProvider,
) : WireguardHandshakeMonitor.Listener {

    private var failureRecoveryInProgress = AtomicBoolean(false)
    private val job = ConflatedJob()

    override suspend fun onTunnelFailure(
        coroutineScope: CoroutineScope,
        lastHandshakeEpocSeconds: Long,
    ) {
        val nowSeconds = currentTimeProvider.getTimeInEpochSeconds()
        val diff = nowSeconds - lastHandshakeEpocSeconds
        if (diff.seconds.inWholeMinutes >= FAILURE_RECOVERY_THRESHOLD_MINUTES && !failureRecoveryInProgress.get()) {
            logcat { "Failure recovery: starting recovery" }
            failureRecoveryInProgress.set(true)
            job += coroutineScope.launch(dispatcherProvider.io()) {
                incrementalPeriodicChecks {
                    attemptRecovery()
                }
            }
        } else {
            if (failureRecoveryInProgress.get()) {
                logcat { "Failure recovery: Recovery already in progress. Do nothing" }
            } else {
                logcat { "Failure recovery: time since lastHandshakeEpocSeconds is not within failure recovery threshold" }
            }
        }
    }

    override suspend fun onTunnelFailureRecovered(coroutineScope: CoroutineScope) {
        logcat { "Failure recovery: tunnel recovered, cancelling recovery" }
        job.cancel()
        wgTunnel.markTunnelHealthy()
        failureRecoveryInProgress.set(false)
    }

    private suspend fun incrementalPeriodicChecks(
        times: Int = 140, // Adding a cap of around 12 hours - ideally a device should recover around that time.
        initialDelay: Long = 30_000, // 30 seconds
        maxDelay: Long = 300_000, // 5 minutes
        factor: Double = 2.0,
        block: suspend () -> Unit,
    ) {
        var currentDelay = initialDelay
        repeat(times) {
            try {
                if (failureRecoveryInProgress.get()) {
                    block()
                } else {
                    return@incrementalPeriodicChecks
                }
            } catch (t: Throwable) {
                // you can log an error here and/or make a more finer-grained
                // analysis of the cause to see if retry is needed
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }

    private suspend fun attemptRecovery(): Result<Unit> {
        logcat { "Failure recovery: attemptRecovery" }

        if (vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) {
            networkProtectionPixels.reportFailureRecoveryStarted()
            wgTunnel.markTunnelUnhealthy()
            val (currentServer, currentTunAddresses) = with(wgTunnelConfig.getWgConfig()) {
                this?.asServerDetails()?.serverName to this?.`interface`?.addresses.orEmpty()
            }

            // Create a new config using the same key
            val config = wgTunnel.createWgConfig()
                .onFailure {
                    networkProtectionPixels.reportFailureRecoveryFailed()
                    logcat(ERROR) { "Failure recovery: Failed registering the new key:  ${it.asLog()}" }
                }.getOrElse {
                    return Result.failure(it)
                }

            logcat { "Failure recovery: current server: $currentServer config server: ${config.asServerDetails().serverName}" }
            if (config.asServerDetails().serverName != currentServer || !config.`interface`.addresses.containsAll(currentTunAddresses)) {
                logcat { "Failure recovery: Restarting VPN to connect to new server" }
                // Store the created config since it contains a new server
                networkProtectionPixels.reportFailureRecoveryCompletedWithServerUnhealthy()
                if (!config.`interface`.addresses.containsAll(currentTunAddresses)) {
                    networkProtectionPixels.reportFailureRecoveryCompletedWithDifferentTunnelAddress()
                }

                wgTunnel.markTunnelHealthy()
                wgTunnelConfig.setWgConfig(config)
                vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
            } else {
                networkProtectionPixels.reportFailureRecoveryCompletedWithServerHealthy()
                // Ignore created config, new keypair should eventually be ignored by the controller
                logcat { "Failure recovery: server is healthy, nothing to do." }
            }
        } else {
            logcat { "Failure recovery: Ignore attempted recovery due to VPN being off" }
        }

        return Result.success(Unit)
    }

    companion object {
        // WG handshakes happen every 2min, If we missed 7+ handshakes, we start the failure recovery
        private const val FAILURE_RECOVERY_THRESHOLD_MINUTES = 15
    }
}
