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
import java.net.InetAddress

interface VpnNetworkStack {

    /** Name of the networking layer */
    val name: String

    /**
     * Called before the networking layer is enabled
     *
     * @return `true` if the networking layer is successfully created, `false` otherwise
     */
    fun onCreateVpn(): Result<Unit>

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
    fun onStopVpn(): Result<Unit>

    /**
     * Clean when the networking layer is destroyed. You can use this method to clean up resources
     *
     * @return `true` if the networking layer is successfully destroyed, `false` otherwise
     */
    fun onDestroyVpn(): Result<Unit>

    /**
     * @return the MTU size you wish the VPN service to set
     */
    fun mtu(): Int

    /**
     * @return the address you wish to add to the VPN service.
     * They key contains the InetAddress of the address and value should be the mask width.
     */
    fun addresses(): Map<InetAddress, Int>

    /**
     * @return the additional dns servers you wish to add to the VPN service
     */
    fun dns(): Set<InetAddress>

    /**
     * @return the additional routes you wish to add to the VPN service.
     * They key contains the InetAddress of the route and value should be the mask width.
     */
    fun routes(): Map<InetAddress, Int>
}
