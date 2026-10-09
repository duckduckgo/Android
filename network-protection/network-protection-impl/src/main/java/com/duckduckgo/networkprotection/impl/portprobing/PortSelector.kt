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
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

interface PortSelector {
    /**
     * Selects the best port from the probe result
     * @param respondedPorts the ports that responded to the probe
     * @param currentPort the port the tunnel is currently using
     * @param serverDefaultPort the server's default port
     * @param advertisedPorts all ports advertised by the server
     * @return the port to use
     */
    fun selectPort(
        respondedPorts: List<Long>,
        currentPort: Long,
        serverDefaultPort: Long,
        advertisedPorts: List<Long>,
    ): Long

    /**
     * Orders candidate ports for probing
     * @param serverDefaultPort the server's default port
     * @param advertisedPorts all ports advertised by the server
     * @return ordered list of ports to probe
     */
    fun orderCandidatePorts(
        serverDefaultPort: Long,
        advertisedPorts: List<Long>,
    ): List<Long>
}

@ContributesBinding(VpnScope::class)
class RealPortSelector @Inject constructor() : PortSelector {

    override fun selectPort(
        respondedPorts: List<Long>,
        currentPort: Long,
        serverDefaultPort: Long,
        advertisedPorts: List<Long>,
    ): Long {
        val fallbackPort = respondedPorts.firstOrNull()
        return when {
            respondedPorts.contains(serverDefaultPort) -> {
                logcat { "Port selection: using default port $serverDefaultPort" }
                serverDefaultPort
            }
            fallbackPort != null -> {
                logcat { "Port selection: using fallback port $fallbackPort" }
                fallbackPort
            }
            currentPort > 0 && advertisedPorts.contains(currentPort) -> {
                logcat { "Port selection: keeping current port $currentPort (no probe response)" }
                currentPort
            }
            else -> {
                logcat { "Port selection: falling back to server default $serverDefaultPort (no probe response)" }
                serverDefaultPort
            }
        }
    }

    override fun orderCandidatePorts(
        serverDefaultPort: Long,
        advertisedPorts: List<Long>,
    ): List<Long> {
        return buildList {
            if (serverDefaultPort > 0 && advertisedPorts.contains(serverDefaultPort)) {
                add(serverDefaultPort)
            }
            advertisedPorts.forEach { port ->
                if (port > 0 && !contains(port)) {
                    add(port)
                }
            }
        }.also {
            logcat { "Port selection: ordered candidates: $it" }
        }
    }
}
