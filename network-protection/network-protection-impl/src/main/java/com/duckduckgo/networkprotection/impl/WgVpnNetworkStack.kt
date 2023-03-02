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
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.networkprotection.impl.config.NetPConfigProvider
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelDataProvider
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelDataProvider.WgTunnelData
import com.duckduckgo.networkprotection.impl.configuration.toCidrString
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ClientInterface
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ServerDetails
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.SingleInstanceIn
import javax.inject.Inject
import logcat.LogPriority
import logcat.asLog
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
    private val netpPixels: Lazy<NetworkProtectionPixels>,
    private val netPConfigProvider: NetPConfigProvider,
) : VpnNetworkStack {
    private var wgTunnelData: WgTunnelData? = null

    override val name: String = NetPVpnFeature.NETP_VPN.featureName

    override fun onCreateVpn(): Result<Unit> = Result.success(Unit)

    override suspend fun onPrepareVpn(): Result<VpnTunnelConfig> {
        return try {
            wgTunnelData = configProvider.get().get()
            logcat { "Received config from BE: $wgTunnelData" }

            if (wgTunnelData == null) {
                logcat(LogPriority.ERROR) { "Unable to construct wgTunnelData" }
                netpPixels.get().reportErrorInRegistration()
                return Result.failure(java.lang.IllegalStateException("Unable to construct wgTunnelData"))
            }

            networkProtectionRepository.get().run {
                serverDetails = ServerDetails(
                    ipAddress = wgTunnelData!!.serverIP,
                    location = wgTunnelData!!.serverLocation,
                )
                clientInterface = ClientInterface(
                    tunnelCidrSet = wgTunnelData!!.tunnelAddress.toCidrString(),
                )
            }

            Result.success(
                VpnTunnelConfig(
                    mtu = netPConfigProvider.mtu(),
                    addresses = wgTunnelData?.tunnelAddress ?: emptyMap(),
                    dns = netPConfigProvider.dns(),
                    routes = emptyMap(),
                    appExclusionList = netPConfigProvider.exclusionList(),
                ),
            ).also { logcat { "Returning VPN configuration: ${it.getOrNull()}" } }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "onPrepareVpn failed due to ${e.asLog()}" }
            Result.failure(e)
        }
    }

    override fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit> {
        logcat { "onStartVpn called." }
        return turnOnNative(tunfd.detachFd())
    }

    override fun onStopVpn(reason: VpnStopReason): Result<Unit> {
        logcat { "onStopVpn called." }
        return turnOffNative(reason)
    }

    override fun onDestroyVpn(): Result<Unit> {
        logcat { "onDestroyVpn called." }
        return Result.success(Unit)
    }

    private fun turnOnNative(tunfd: Int): Result<Unit> {
        if (wgTunnelData == null) {
            netpPixels.get().reportErrorWgInvalidState()
            return Result.failure(java.lang.IllegalStateException("Tunnel data not available when attempting to start wg."))
        }

        val result = wgProtocol.get().startWg(
            tunfd,
            wgTunnelData!!.userSpaceConfig.also {
                logcat { "WgUserspace config: $it" }
            },
            pcapConfig = netPConfigProvider.pcapConfig(),
        )
        return if (result.isFailure) {
            logcat(LogPriority.ERROR) { "Failed to turnOnNative due to ${result.exceptionOrNull()}" }
            netpPixels.get().reportErrorWgBackendCantStart()
            result
        } else {
            logcat { "Completed turnOnNative" }

            // Only update if enabledTimeInMillis has been reset
            if (networkProtectionRepository.get().enabledTimeInMillis == -1L) {
                networkProtectionRepository.get().enabledTimeInMillis = currentTimeProvider.get()
            }
            Result.success(Unit)
        }
    }

    private fun turnOffNative(reason: VpnStopReason): Result<Unit> {
        logcat { "turnOffNative wg" }

        logcat { "Started turnOffNative" }
        kotlin.runCatching {
            wgProtocol.get().stopWg()
        }.onFailure {
            logcat(LogPriority.ERROR) { "WG network: ${it.asLog()}" }
        }
        logcat { "Completed turnOffNative" }

        networkProtectionRepository.get().serverDetails = null

        // Only update if enabledTimeInMillis stop has been initiated by the user
        if (reason == SELF_STOP) {
            networkProtectionRepository.get().enabledTimeInMillis = -1
        }
        return Result.success(Unit)
    }
}
