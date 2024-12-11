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
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.anrs.api.CrashLogger.Crash
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.network.DnsProvider
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.RESTART
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.computeBlockMalwareDnsOrSame
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import com.wireguard.config.Config
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
    private val wgTunnelLazy: Lazy<WgTunnel>,
    private val wgTunnelConfigLazy: Lazy<WgTunnelConfig>,
    private val networkProtectionRepository: Lazy<NetworkProtectionRepository>,
    private val netPDefaultConfigProvider: NetPDefaultConfigProvider,
    private val currentTimeProvider: CurrentTimeProvider,
    private val netpPixels: Lazy<NetworkProtectionPixels>,
    private val dnsProvider: DnsProvider,
    private val crashLogger: CrashLogger,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val vpnRemoteFeatures: VpnRemoteFeatures,
) : VpnNetworkStack {
    private var wgConfig: Config? = null

    override val name: String = NetPVpnFeature.NETP_VPN.featureName

    override fun onCreateVpn(): Result<Unit> = Result.success(Unit)

    override suspend fun onPrepareVpn(): Result<VpnTunnelConfig> {
        return try {
            netpPixels.get().reportEnableAttempt()

            wgConfig = wgTunnelLazy.get().createAndSetWgConfig()
                .onFailure { netpPixels.get().reportErrorInRegistration() }
                .getOrThrow()
            logcat { "Wireguard configuration:\n$wgConfig" }

            val privateDns = dnsProvider.getPrivateDns()
            val dns = if (netPSettingsLocalConfig.blockMalware().isEnabled() && vpnRemoteFeatures.allowBlockMalware().isEnabled()) {
                // if the user has configured "block malware" we calculate the malware DNS from the DDG default DNS(s)
                wgConfig!!.`interface`.dnsServers.map { it.computeBlockMalwareDnsOrSame() }.toSet()
            } else {
                wgConfig!!.`interface`.dnsServers
            }
            Result.success(
                VpnTunnelConfig(
                    mtu = wgConfig?.`interface`?.mtu ?: 1280,
                    addresses = wgConfig!!.`interface`.addresses.associate { Pair(it.address, it.mask) },
                    // when Android private DNS are set, we return DO NOT configure any DNS.
                    // why? no use intercepting encrypted DNS traffic, plus we can't configure any DNS that doesn't support DoT, otherwise Android
                    // will enforce DoT and will stop passing any DNS traffic, resulting in no DNS resolution == connectivity is killed
                    dns = if (privateDns.isEmpty()) dns else emptySet(),
                    customDns = if (privateDns.isEmpty()) netPDefaultConfigProvider.fallbackDns() else emptySet(),
                    routes = wgConfig!!.`interface`.routes.associate { it.address.hostAddress!! to it.mask },
                    appExclusionList = wgConfig!!.`interface`.excludedApplications,
                ),
            ).also { logcat { "Returning VPN configuration: ${it.getOrNull()}" } }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "onPrepareVpn failed due to ${e.asLog()}" }
            crashLogger.logCrash(Crash("vpn_on_prepare_error", e))
            Result.failure(e)
        }.onFailure {
            netpPixels.get().reportEnableAttemptFailure()
        }
    }

    override fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit> {
        logcat { "onStartVpn called." }
        return turnOnNative(tunfd.detachFd())
            .onSuccess { netpPixels.get().reportEnableAttemptSuccess() }
            .onFailure { netpPixels.get().reportEnableAttemptFailure() }
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
        if (wgConfig == null) {
            netpPixels.get().reportErrorWgInvalidState()
            return Result.failure(java.lang.IllegalStateException("Tunnel data not available when attempting to start wg."))
        }

        val result = wgProtocol.get().startWg(
            tunfd,
            wgConfig!!.toWgUserspaceString().also {
                logcat { "WgUserspace config: $it" }
            },
            // pcapConfig = netPDefaultConfigProvider.pcapConfig(),
            pcapConfig = null,
        )
        return if (result.isFailure) {
            logcat(LogPriority.ERROR) { "Failed to turnOnNative due to ${result.exceptionOrNull()}" }
            netpPixels.get().reportErrorWgBackendCantStart()
            result
        } else {
            logcat { "Completed turnOnNative" }

            // Only update if enabledTimeInMillis has been reset
            if (networkProtectionRepository.get().enabledTimeInMillis == -1L) {
                networkProtectionRepository.get().enabledTimeInMillis = currentTimeProvider.getTimeInMillis()
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

        if (reason != RESTART) {
            logcat { "Deleting wireguard config..." }
            wgTunnelConfigLazy.get().clearWgConfig()
        }

        // Only update if enabledTimeInMillis stop has been initiated by the user
        if (reason is SELF_STOP && reason.snoozedTriggerAtMillis == 0L) {
            networkProtectionRepository.get().enabledTimeInMillis = -1
        }
        return Result.success(Unit)
    }
}
