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
import javax.inject.Inject

/**
 * Result of port probing and selection
 * @property selectedPort the port the tunnel should use
 * @property shouldSwitchPort true when another port responded but the port the tunnel is using did not
 */
data class PortProbeResult(
    val selectedPort: Long,
    val shouldSwitchPort: Boolean,
)

/**
 * Coordinates port probing workflow: reads port state, probes ports and selects the best port.
 */
interface PortProbingCoordinator {
    /**
     * Probes available ports and selects the best one
     * @return probe result, or null if probing should be skipped
     */
    suspend fun probeAndSelect(): PortProbeResult?
}

@ContributesBinding(VpnScope::class)
class RealPortProbingCoordinator @Inject constructor(
    private val portProber: PortProber,
    private val portSelector: PortSelector,
    private val wgTunnelConfig: WgTunnelConfig,
) : PortProbingCoordinator {

    override suspend fun probeAndSelect(): PortProbeResult? {
        val config = wgTunnelConfig.getWgConfig()
        val serverIp = config?.serverIp() ?: return null
        val advertisedPorts = wgTunnelConfig.getAdvertisedPorts()

        if (advertisedPorts.size < 2) return null

        val serverDefaultPort = wgTunnelConfig.getServerDefaultPort()
        val currentPort = wgTunnelConfig.getActivePort().takeIf { it > 0 } ?: config.currentEndpointPort()

        val candidatePorts = portSelector.orderCandidatePorts(
            serverDefaultPort = serverDefaultPort,
            advertisedPorts = advertisedPorts,
        )

        // Both answering fully determines the outcome: the default is selected and the current port is known to work
        val earlyExitPorts = setOf(serverDefaultPort, currentPort).filter { it in candidatePorts }.toSet()
        val respondedPorts = portProber.probePortsInParallel(serverIp, candidatePorts, earlyExitPorts)

        val selectedPort = portSelector.selectPort(
            respondedPorts = respondedPorts,
            currentPort = currentPort,
            serverDefaultPort = serverDefaultPort,
            advertisedPorts = advertisedPorts,
        )

        return PortProbeResult(
            selectedPort = selectedPort,
            shouldSwitchPort = respondedPorts.isNotEmpty() && !respondedPorts.contains(currentPort),
        )
    }
}
