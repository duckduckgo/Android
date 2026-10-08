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

data class PortSelectionResult(
    val selectedPort: Long,
    val shouldRemember: Boolean,
)

interface PortSelector {
    /**
     * Selects the best port from the probe result
     * @param probedPort the port that responded to the probe, or null if none
     * @param currentPort the currently configured port
     * @param serverDefaultPort the server's default port
     * @param advertisedPorts all ports advertised by the server
     * @return the port to use and whether it should be remembered
     */
    fun selectPort(
        probedPort: Long?,
        currentPort: Long,
        serverDefaultPort: Long,
        advertisedPorts: List<Long>,
    ): PortSelectionResult

    /**
     * Orders candidate ports for probing
     * @param rememberedPort the last successful port, or 0 if none
     * @param serverDefaultPort the server's default port
     * @param advertisedPorts all ports advertised by the server
     * @return ordered list of ports to probe
     */
    fun orderCandidatePorts(
        rememberedPort: Long,
        serverDefaultPort: Long,
        advertisedPorts: List<Long>,
    ): List<Long>
}

@ContributesBinding(VpnScope::class)
class RealPortSelector @Inject constructor() : PortSelector {

    override fun selectPort(
        probedPort: Long?,
        currentPort: Long,
        serverDefaultPort: Long,
        advertisedPorts: List<Long>,
    ): PortSelectionResult {
        return when {
            // Default port answered - use it and remember it
            probedPort == serverDefaultPort -> {
                logcat { "Port selection: using default port $serverDefaultPort" }
                PortSelectionResult(serverDefaultPort, shouldRemember = true)
            }
            // Some other port answered - use it and remember it
            probedPort != null -> {
                logcat { "Port selection: using fallback port $probedPort" }
                PortSelectionResult(probedPort, shouldRemember = true)
            }
            // Nothing answered but current port is still advertised - keep it
            currentPort > 0 && advertisedPorts.contains(currentPort) -> {
                logcat { "Port selection: keeping current port $currentPort (no probe response)" }
                PortSelectionResult(currentPort, shouldRemember = false)
            }
            // Nothing answered and current port not advertised - use server default
            else -> {
                logcat { "Port selection: falling back to server default $serverDefaultPort (no probe response)" }
                PortSelectionResult(serverDefaultPort, shouldRemember = false)
            }
        }
    }

    override fun orderCandidatePorts(
        rememberedPort: Long,
        serverDefaultPort: Long,
        advertisedPorts: List<Long>,
    ): List<Long> {
        return buildList {
            // Server default port ALWAYS first - we prefer it if it works
            if (serverDefaultPort > 0 && advertisedPorts.contains(serverDefaultPort)) {
                add(serverDefaultPort)
            }

            // Remembered port second (if different from default)
            if (rememberedPort > 0 && rememberedPort != serverDefaultPort && advertisedPorts.contains(rememberedPort)) {
                add(rememberedPort)
            }

            // Remaining advertised ports
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
