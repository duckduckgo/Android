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
import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
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
    private val netPDebugMtuProvider: PluginPoint<NetPDebugMtuProvider>,
    private val exclusionListProvider: PluginPoint<NetPDebugExclusionListProvider>,
    private val appBuildConfig: AppBuildConfig,
) : VpnNetworkStack {
    private var wgTunnelData: WgTunnelData? = null

    override val name: String = NetPVpnFeature.NETP_VPN.featureName

    override fun onCreateVpn(): Result<Unit> = Result.success(Unit)

    override suspend fun onPrepareVpn(): Result<VpnTunnelConfig> {
        fun PluginPoint<NetPDebugMtuProvider>.getMtu(): Int {
            if (appBuildConfig.isInternalBuild()) {
                assert(this.getPlugins().size <= 1) { "Only one NetPDebugMtuProvider should be registered" }
                return this.getPlugins().firstOrNull()?.getMtu() ?: 1280
            }

            return 1280
        }
        fun PluginPoint<NetPDebugExclusionListProvider>.getExclusionList(): Set<String> {
            if (appBuildConfig.isInternalBuild()) {
                assert(this.getPlugins().size <= 1) { "Only one NetPDebugMtuProvider should be registered" }
                return this.getPlugins().firstOrNull()?.getExclusionList() ?: emptySet()
            }

            return emptySet()
        }

        return try {
            wgTunnelData = configProvider.get().get()
            logcat { "Received config from BE: $wgTunnelData" }

            networkProtectionRepository.get().serverDetails = ServerDetails(
                ipAddress = wgTunnelData!!.serverIP,
                location = wgTunnelData!!.serverLocation,
            )

            Result.success(
                VpnTunnelConfig(
                    mtu = netPDebugMtuProvider.getMtu(),
                    addresses = wgTunnelData?.tunnelAddress ?: emptyMap(),
                    dns = emptySet(),
                    routes = emptyMap(),
                    appExclusionList = exclusionListProvider.getExclusionList(),
                ),
            ).also { logcat { "Returning VPN configuration: ${it.getOrNull()}" } }
        } catch (e: Exception) {
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
            return Result.failure(BadConfigException(CONFIG, TOP_LEVEL, Reason.MISSING_SECTION, "Config could not be empty."))
        }
        logcat { "Thread: Started turnOnNative" }
        wgProtocol.get().startWg(
            tunfd,
            wgTunnelData!!.userSpaceConfig.also {
                logcat { "WgUserspace config: $it" }
            },
        )
        logcat { "Thread: Completed turnOnNative" }

        // Only update if enabledTimeInMillis has been reset
        if (networkProtectionRepository.get().enabledTimeInMillis == -1L) {
            networkProtectionRepository.get().enabledTimeInMillis = currentTimeProvider.get()
        }
        return Result.success(Unit)
    }

    private fun turnOffNative(reason: VpnStopReason): Result<Unit> {
        logcat { "turnOffNative wg" }

        logcat { "Thread: Started turnOffNative" }
        kotlin.runCatching {
            wgProtocol.get().stopWg()
        }.onFailure {
            logcat { "WG network: ${it.asLog()}" }
        }
        logcat { "Thread: Completed turnOffNative" }

        networkProtectionRepository.get().serverDetails = null

        // Only update if enabledTimeInMillis stop has been initiated by the user
        if (reason == SELF_STOP) {
            networkProtectionRepository.get().enabledTimeInMillis = -1
        }
        return Result.success(Unit)
    }
}

@ContributesPluginPoint(VpnScope::class)
interface NetPDebugMtuProvider {
    fun getMtu(): Int
}

@ContributesPluginPoint(VpnScope::class)
interface NetPDebugExclusionListProvider {
    fun getExclusionList(): Set<String>
}
