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

import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint

/**
 * Creates a new Config with the endpoint port replaced
 * @param newPort the new port to use
 * @return a new Config with updated endpoint port
 */
fun Config.replacingEndpointPort(newPort: Long): Config {
    val currentPeer = this.peers.firstOrNull() ?: return this
    val currentEndpoint = currentPeer.endpoint ?: return this

    val newEndpoint = InetEndpoint.parse("${currentEndpoint.host}:$newPort")

    val newPeer = currentPeer.builder
        .parseEndpoint(newEndpoint.toString())
        .build()

    return Config.Builder()
        .setInterface(this.`interface`)
        .addPeer(newPeer)
        .build()
}

/**
 * Extracts the current endpoint port from the config
 * @return the current port, or 0 if no endpoint is configured
 */
fun Config.currentEndpointPort(): Long {
    return this.peers.firstOrNull()?.endpoint?.port?.toLong() ?: 0L
}

/**
 * Extracts the server IP from the config
 * @return the server IP address, or null if no endpoint is configured
 */
fun Config.serverIp(): String? {
    return this.peers.firstOrNull()?.endpoint?.getResolved()?.host
}
