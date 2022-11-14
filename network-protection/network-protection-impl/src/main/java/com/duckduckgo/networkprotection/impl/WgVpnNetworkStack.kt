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

package com.duckduckgo.networkprotection.impl

import android.os.ParcelFileDescriptor
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.config.Config
import dagger.SingleInstanceIn
import timber.log.Timber
import java.net.InetAddress
import javax.inject.Inject

@ContributesBinding(
    scope = VpnScope::class,
    boundType = VpnNetworkStack::class
)
@SingleInstanceIn(VpnScope::class)
class WgVpnNetworkStack @Inject constructor(
    private val wgProtocol: WgProtocol,
    configProvider: WgConfigProvider
) : VpnNetworkStack {
    private var wgThread: Thread? = null
    private val config: Config = configProvider.get()
    override val name: String = "wireguard"

    override fun onCreateVpn(): Result<Unit> {
        return Result.success(Unit)
    }

    override fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit> {
        Timber.d("onStartVpn called.")
        return turnOnNative(tunfd.fd)
    }

    override fun onStopVpn(): Result<Unit> {
        Timber.d("onStopVpn called.")
        return turnOffNative()
    }

    override fun onDestroyVpn(): Result<Unit> {
        Timber.d("onDestroyVpn called.")
        return Result.success(Unit)
    }

    override fun mtu(): Int = 1_500


    override fun addresses(): Map<InetAddress, Int> = config.getInterface().addresses.associate { Pair(it.address, it.mask) }

    override fun dns(): Set<InetAddress> = config.getInterface().dnsServers

    override fun routes(): Map<InetAddress, Int> = config.getInterface().dnsServers.associateWith { 32 }

    private fun turnOnNative(tunfd: Int): Result<Unit> {
        if (wgThread == null) {
            Timber.d("turnOnNative wg")

            wgThread = Thread {
                Timber.d("Thread: Started turnOnNative")
                wgProtocol.startWg(tunfd, config.toWgUserspaceString())
                Timber.d("Thread: Completed turnOnNative")
                wgThread = null
            }.also { it.start() }
        }

        return Result.success(Unit)
    }

    private fun turnOffNative(): Result<Unit> {
        if (wgThread == null) {
            Timber.d("turnOffNative wg")

            wgThread = Thread {
                Timber.d("Thread: Started turnOffNative")
                wgProtocol.stopWg()
                Timber.d("Thread: Completed turnOffNative")
                wgThread = null
            }.also { it.start() }
        }

        return Result.success(Unit)
    }
}
