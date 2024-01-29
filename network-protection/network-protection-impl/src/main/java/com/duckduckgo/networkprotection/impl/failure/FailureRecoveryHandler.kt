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
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.crypto.KeyPair
import javax.inject.Inject
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

interface FailureRecoveryHandler {
    suspend fun attemptRecovery(): Result<Unit>
}

@ContributesBinding(VpnScope::class)
class RealFailureRecoveryHandler @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val wgTunnel: WgTunnel,
    private val wgTunnelConfig: WgTunnelConfig,
) : FailureRecoveryHandler {
    override suspend fun attemptRecovery(): Result<Unit> {
        logcat { "Failure recovery: attemptRecovery" }

        if (vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) {
            val currentServer = wgTunnelConfig.getWgConfig()?.asServerDetails()?.serverName
            val config = wgTunnel.createWgConfig(KeyPair(), fromFailure = true)
                .onFailure {
                    logcat(LogPriority.ERROR) { "Failure recovery: Failed registering the new key:  ${it.asLog()}" }
                }.getOrElse {
                    return Result.failure(it)
                }

            if (config.asServerDetails().serverName != currentServer) {
                logcat { "Failure recovery: Restarting VPN to connect to new server" }
                vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
            } else {
                logcat { "Failure recovery: server is healthy, nothing to do." }
            }
        } else {
            logcat { "Failure recovery: Ignore attempted recovery due to VPN being off" }
        }

        return Result.success(Unit)
    }
}
