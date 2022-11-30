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
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.WgConfigProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.config.BadConfigException
import com.wireguard.config.BadConfigException.Location.TOP_LEVEL
import com.wireguard.config.BadConfigException.Reason
import com.wireguard.config.BadConfigException.Section.CONFIG
import com.wireguard.config.Config
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(
    scope = VpnScope::class,
    boundType = VpnNetworkStack::class,
)
@SingleInstanceIn(VpnScope::class)
class WgVpnNetworkStack @Inject constructor(
    private val wgProtocol: Lazy<WgProtocol>,
    private val configProvider: Lazy<WgConfigProvider>,
    private val dispatcherProvider: DispatcherProvider
) : VpnNetworkStack {
    private var wgThread: Thread? = null
    private var config: Config? = null
    override val name: String = "wireguard"

    override fun onCreateVpn(): Result<Unit> = Result.success(Unit)

    override fun onPrepareVpn(): Result<VpnTunnelConfig> {
        return try {
            runBlocking(dispatcherProvider.io()) {
                config = configProvider.get().get().also {
                    Timber.d("Received config from BE: $it")
                }
            }
            Result.success(
                VpnTunnelConfig(
                    mtu = 1420,
                    addresses = config?.getInterface()?.addresses?.associate { Pair(it.address, it.mask) } ?: emptyMap(),
                    dns = emptySet(),
                    routes = emptyMap(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    private fun turnOnNative(tunfd: Int): Result<Unit> {
        if (config == null) {
            return Result.failure(BadConfigException(CONFIG, TOP_LEVEL, Reason.MISSING_SECTION, "Config could not be empty."))
        }
        if (wgThread == null) {
            Timber.d("turnOnNative wg")

            wgThread = Thread {
                Timber.d("Thread: Started turnOnNative")
                wgProtocol.get().startWg(
                    tunfd,
                    config!!.toWgUserspaceString().also {
                        Timber.d("WgUserspace config: $it")
                    },
                )
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
                wgProtocol.get().stopWg()
                Timber.d("Thread: Completed turnOffNative")
                wgThread = null
            }.also { it.start() }
        }

        return Result.success(Unit)
    }
}
