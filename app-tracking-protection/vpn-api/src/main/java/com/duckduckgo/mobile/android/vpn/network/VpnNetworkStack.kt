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

package com.duckduckgo.mobile.android.vpn.network

import android.os.ParcelFileDescriptor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import java.net.InetAddress

interface VpnNetworkStack {

    /** Name of the networking layer */
    val name: String

    /**
     *
     * @return `true` if the networking layer is successfully created, `false` otherwise
     */
    fun onCreateVpn(): Result<Unit>

    /**
     * Called before the vpn tunnel is created and before the vpn is started.
     *
     * @return VpnTunnelConfig that will be used to configures the VPN's tunnel.
     *
     * The signature of this method is [suspend] to allow for asynchronous operations when creating the [VpnTunnelConfig].
     */
    suspend fun onPrepareVpn(): Result<VpnTunnelConfig>

    /**
     * Called before the VPN is started
     *
     * @return `true` if the VPN is successfully started, `false` otherwise
     */
    fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit>

    /**
     * Called before the VPN is stopped
     *
     * @return `true` if the VPN is successfully stopped, `false` otherwise
     */
    fun onStopVpn(reason: VpnStopReason): Result<Unit>

    /**
     * Clean when the networking layer is destroyed. You can use this method to clean up resources
     *
     * @return `true` if the networking layer is successfully destroyed, `false` otherwise
     */
    fun onDestroyVpn(): Result<Unit>

    /**
     * Additional configuration data to be set to the VPN tunnel
     *
     * @param mtu the MTU size you wish the VPN service to set
     * @param addresses the address you wish to set to the VPN service. They key contains the InetAddress of the address and
     * value should be the mask width.
     * @param dns the additional dns servers you wish to add to the VPN service
     * @param searchDomains the DNS domains search path. Comma separated domains to search when resolving host names or null
     * @param routes the routes (if any) you wish to add to the VPN service.
     * The Map<String, Int> contains the String IP address and the Int mask.
     * If no routes are returned, the VPN will apply its own defaults.
     * @param appExclusionList the list of apps you wish to exclude from the VPN tunnel
     */
    data class VpnTunnelConfig(
        val mtu: Int,
        val addresses: Map<InetAddress, Int>,
        val dns: Set<InetAddress>,
        val searchDomains: String? = null,
        val customDns: Set<InetAddress>,
        val routes: Map<String, Int>,
        val appExclusionList: Set<String>,
    )

    companion object EmptyVpnNetworkStack : VpnNetworkStack {
        override val name = "empty"

        override fun onCreateVpn(): Result<Unit> {
            return Result.failure(Exception("EmptyVpnNetworkStack"))
        }

        override suspend fun onPrepareVpn(): Result<VpnTunnelConfig> {
            return Result.failure(Exception("EmptyVpnNetworkStack"))
        }

        override fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit> {
            return Result.failure(Exception("EmptyVpnNetworkStack"))
        }

        override fun onStopVpn(reason: VpnStopReason): Result<Unit> {
            return Result.failure(Exception("EmptyVpnNetworkStack"))
        }

        override fun onDestroyVpn(): Result<Unit> {
            return Result.failure(Exception("EmptyVpnNetworkStack"))
        }
    }
}
