/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.portprobing

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.config.Config
import javax.inject.Inject

/**
 * Result of port probing and selection
 */
data class PortProbeResult(
    val probedPort: Long?,
    val selectedPort: Long,
    val shouldRemember: Boolean,
    val portChanged: Boolean,
)

/**
 * Coordinates port probing workflow: reads port state, probes ports, selects best port,
 * and remembers successful ports.
 */
interface PortProbingCoordinator {
    /**
     * Probes available ports and selects the best one
     * @param config the current WireGuard config
     * @param wgTunnelConfig tunnel config for reading/writing port state
     * @return probe result, or null if probing should be skipped
     */
    suspend fun probeAndSelect(
        config: Config,
        wgTunnelConfig: WgTunnelConfig,
    ): PortProbeResult?
}

@ContributesBinding(VpnScope::class)
class RealPortProbingCoordinator @Inject constructor(
    private val portProber: PortProber,
    private val portSelector: PortSelector,
) : PortProbingCoordinator {

    override suspend fun probeAndSelect(
        config: Config,
        wgTunnelConfig: WgTunnelConfig,
    ): PortProbeResult? {
        val serverIp = config.serverIp() ?: return null
        val advertisedPorts = wgTunnelConfig.getAdvertisedPorts()

        if (advertisedPorts.isEmpty() || advertisedPorts.size == 1) return null

        val serverDefaultPort = wgTunnelConfig.getServerDefaultPort()
        val rememberedPort = wgTunnelConfig.getRememberedPort()
        val currentPort = config.currentEndpointPort()

        val candidatePorts = portSelector.orderCandidatePorts(
            rememberedPort = rememberedPort,
            serverDefaultPort = serverDefaultPort,
            advertisedPorts = advertisedPorts,
        )

        val probedPort = portProber.probePortsInParallel(serverIp, candidatePorts)

        val selectionResult = portSelector.selectPort(
            probedPort = probedPort,
            currentPort = currentPort,
            serverDefaultPort = serverDefaultPort,
            advertisedPorts = advertisedPorts,
        )

        if (selectionResult.shouldRemember) {
            wgTunnelConfig.setRememberedPort(selectionResult.selectedPort)
        }

        return PortProbeResult(
            probedPort = probedPort,
            selectedPort = selectionResult.selectedPort,
            shouldRemember = selectionResult.shouldRemember,
            portChanged = selectionResult.selectedPort != currentPort,
        )
    }
}
