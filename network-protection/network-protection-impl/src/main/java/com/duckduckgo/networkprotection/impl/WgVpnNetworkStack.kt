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
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelDataProvider
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelDataProvider.WgTunnelData
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ServerDetails
import com.squareup.anvil.annotations.ContributesMultibinding
import com.wireguard.config.BadConfigException
import com.wireguard.config.BadConfigException.Location.TOP_LEVEL
import com.wireguard.config.BadConfigException.Reason
import com.wireguard.config.BadConfigException.Section.CONFIG
import dagger.Lazy
import dagger.SingleInstanceIn
import javax.inject.Inject
import logcat.logcat

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnNetworkStack::class,
)
@SingleInstanceIn(VpnScope::class)
class WgVpnNetworkStack @Inject constructor(
    private val wgProtocol: Lazy<WgProtocol>,
    private val configProvider: Lazy<WgTunnelDataProvider>,
    private val networkProtectionRepository: Lazy<NetworkProtectionRepository>,
    private val currentTimeProvider: CurrentTimeProvider,
) : VpnNetworkStack {
    private var wgThread: Thread? = null
    private var wgTunnelData: WgTunnelData? = null

    override val name: String = NetPVpnFeature.NETP_VPN.featureName

    override fun onCreateVpn(): Result<Unit> = Result.success(Unit)

    override suspend fun onPrepareVpn(): Result<VpnTunnelConfig> {
        return try {
            wgTunnelData = configProvider.get().get()
            logcat { "Received config from BE: $wgTunnelData" }

            networkProtectionRepository.get().serverDetails = ServerDetails(
                ipAddress = wgTunnelData!!.serverIP,
                location = wgTunnelData!!.serverLocation,
            )

            Result.success(
                VpnTunnelConfig(
                    mtu = 1420,
                    addresses = wgTunnelData?.tunnelAddress ?: emptyMap(),
                    dns = emptySet(),
                    routes = emptyMap(),
                    appExclusionList = emptySet(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit> {
        logcat { "onStartVpn called." }
        return turnOnNative(tunfd.fd)
    }

    override fun onStopVpn(): Result<Unit> {
        logcat { "onStopVpn called." }
        return turnOffNative()
    }

    override fun onDestroyVpn(): Result<Unit> {
        logcat { "onDestroyVpn called." }
        return Result.success(Unit)
    }

    private fun turnOnNative(tunfd: Int): Result<Unit> {
        if (wgTunnelData == null) {
            return Result.failure(BadConfigException(CONFIG, TOP_LEVEL, Reason.MISSING_SECTION, "Config could not be empty."))
        }
        if (wgThread == null) {
            logcat { "turnOnNative wg" }

            wgThread = Thread {
                logcat { "Thread: Started turnOnNative" }
                wgProtocol.get().startWg(
                    tunfd,
                    wgTunnelData!!.userSpaceConfig.also {
                        logcat { "WgUserspace config: $it" }
                    },
                )
                logcat { "Thread: Completed turnOnNative" }
                wgThread = null
            }.also { it.start() }
        }

        networkProtectionRepository.get().enabledTimeInMillis = currentTimeProvider.get()
        return Result.success(Unit)
    }

    private fun turnOffNative(): Result<Unit> {
        if (wgThread == null) {
            logcat { "turnOffNative wg" }

            wgThread = Thread {
                logcat { "Thread: Started turnOffNative" }
                wgProtocol.get().stopWg()
                logcat { "Thread: Completed turnOffNative" }
                wgThread = null
            }.also { it.start() }
        }

        networkProtectionRepository.get().serverDetails = null
        networkProtectionRepository.get().enabledTimeInMillis = -1
        return Result.success(Unit)
    }
}
