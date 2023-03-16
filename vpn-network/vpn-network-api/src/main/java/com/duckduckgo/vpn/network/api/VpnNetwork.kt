/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.vpn.network.api

interface VpnNetwork {
    /**
     * Creates the Networking layer
     * * @return Returns a non-zero network handler identifier if successful or '0' otherwise
     */
    fun create(): Long

    /**
     * Starts the networking layer
     *
     * @param contextId is the network handler identifier returned in [create]
     * @param logLevel is the logging level
     */
    fun start(contextId: Long, logLevel: VpnNetworkLog)

    /**
     * Runs the VPN networking layer. It will start consuming/producing packets from/to the TUN interface
     *
     * @param contextId is the network handler identifier returned in [create]
     * @param tunfd is the TUN interface file descriptor
     */
    fun run(contextId: Long, tunfd: Int)

    /**
     * Stops the VPN networking layer
     * @param contextId is the network handler ID
     */
    fun stop(contextId: Long)

    /**
     * Clears the VPN networking layer resource
     * @param contextId is the network handler ID
     */
    fun destroy(contextId: Long)

    /**
     * @return the MTU size
     */
    fun mtu(): Int

    /**
     * Register a [VpnNetworkCallback] to get notified of some network events
     *
     * @param callback the [VpnNetworkCallback] instance or `null` to unregister previous callback
     */
    fun addCallback(callback: VpnNetworkCallback?)
}

enum class VpnNetworkLog {
    ASSERT,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    VERBOSE,
}
