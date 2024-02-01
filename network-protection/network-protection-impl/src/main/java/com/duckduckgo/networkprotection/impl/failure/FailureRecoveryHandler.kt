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

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.asServerDetails
import com.duckduckgo.networkprotection.impl.pixels.WireguardHandshakeMonitor
import com.squareup.anvil.annotations.ContributesMultibinding
import com.wireguard.crypto.KeyPair
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

@ContributesMultibinding(VpnScope::class)
class FailureRecoveryHandler @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val wgTunnel: WgTunnel,
    private val wgTunnelConfig: WgTunnelConfig,
) : WireguardHandshakeMonitor.Listener {

    private var recoveryCompleted = false
    private var recoveryInProgress = false
    override suspend fun onTunnelFailure(lastHandshakeEpocSeconds: Long) {
        val nowSeconds = Instant.now().epochSecond
        val diff = nowSeconds - lastHandshakeEpocSeconds
        if (diff.seconds.inWholeMinutes >= FAILURE_RECOVERY_THRESHOLD_MINUTES && !recoveryInProgress) {
            recoveryInProgress = true
            recoveryCompleted = false
            incrementalPeriodicChecks {
                recoveryCompleted = attemptRecovery().isSuccess
            }
        } else {
            logcat { "Failure recovery: time since lastHandshakeEpocSeconds is not within failure recovery threshold." }
        }
    }

    override suspend fun onTunnelFailureRecovered() {
        wgTunnel.markTunnelHealthy()
        recoveryCompleted = true
        recoveryInProgress = false
    }

    private suspend fun incrementalPeriodicChecks(
        times: Int = 5,
        initialDelay: Long = 30_000, // 30 seconds
        maxDelay: Long = 300_000, // 5 minutes
        factor: Double = 2.0,
        block: suspend () -> Unit,
    ) {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                if (!recoveryCompleted) {
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
            wgTunnel.markTunnelUnhealthy()
            val currentServer = wgTunnelConfig.getWgConfig()?.asServerDetails()?.serverName

            // Create a new config + register a new keypair
            val config = wgTunnel.createWgConfig(KeyPair())
                .onFailure {
                    logcat(LogPriority.ERROR) { "Failure recovery: Failed registering the new key:  ${it.asLog()}" }
                }.getOrElse {
                    return Result.failure(it)
                }

            logcat { "Failure recovery: current server: $currentServer config server: ${config.asServerDetails().serverName}" }
            if (config.asServerDetails().serverName != currentServer) {
                logcat { "Failure recovery: Restarting VPN to connect to new server" }
                // Store the created config since it contains a new server
                wgTunnel.markTunnelHealthy()
                wgTunnelConfig.setWgConfig(config)
                vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
            } else {
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
