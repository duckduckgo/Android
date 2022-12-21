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

import android.util.Log
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnSocketProtector
import com.duckduckgo.networkprotection.api.NetworkProtectionStatistics
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.android.backend.GoBackend
import com.wireguard.crypto.Key
import dagger.SingleInstanceIn
import javax.inject.Inject
import javax.inject.Provider
import logcat.LogPriority
import logcat.logcat

interface WgProtocol {
    fun startWg(
        tunFd: Int,
        configString: String,
    )

    fun stopWg()
    fun getStatistics(): NetworkProtectionStatistics // TODO: Expose API to Make this consumable from activities
}

@ContributesBinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class RealWgProtocol @Inject constructor(
    private val goBackend: GoBackend,
    private val appBuildConfig: AppBuildConfig,
    private val socketProtector: Provider<VpnSocketProtector>,
) : WgProtocol {
    private var wgTunnel: Int = -1

    override fun startWg(
        tunFd: Int,
        configString: String,
    ) {
        val level = if (appBuildConfig.isDebug) Log.VERBOSE else Log.ASSERT
        wgTunnel = goBackend.wgTurnOn(INTERFACE_NAME, tunFd, configString, level, appBuildConfig.sdkInt)
        if (wgTunnel < 0) {
            logcat(LogPriority.ERROR) { "Wireguard tunnel failed to start: check config / tunFd" }
        } else {
            logcat { "Protecting V4 and V6 wg-sockets" }
            socketProtector.get().protect(goBackend.wgGetSocketV4(wgTunnel))
            socketProtector.get().protect(goBackend.wgGetSocketV6(wgTunnel))
        }
    }

    override fun stopWg() {
        goBackend.wgTurnOff(wgTunnel)
        wgTunnel = -1
    }

    override fun getStatistics(): NetworkProtectionStatistics =
        goBackend.wgGetConfig(wgTunnel)?.toNetworkProtectionStatistics() ?: NetworkProtectionStatistics()

    private fun String.toNetworkProtectionStatistics(): NetworkProtectionStatistics {
        logcat { "Full config $this" }
        var rx = 0L
        var tx = 0L
        var serverIP = ""
        var publicKey = ""
        this.lines().forEach {
            if (it.startsWith("public_key=")) {
                publicKey = Key.fromHex(it.substring(11)).toBase64()
            } else if (it.startsWith("rx_bytes=")) {
                rx = try {
                    it.substring(9).toLong()
                } catch (ignored: NumberFormatException) {
                    0
                }
            } else if (it.startsWith("tx_bytes=")) {
                tx = try {
                    it.substring(9).toLong()
                } catch (ignored: NumberFormatException) {
                    0
                }
            } else if (it.startsWith("endpoint=")) {
                serverIP = it.substring(9)
            }
        }
        return NetworkProtectionStatistics(
            publicKey = publicKey,
            serverIP = serverIP,
            receivedBytes = rx,
            transmittedBytes = tx,
        )
    }

    companion object {
        private const val INTERFACE_NAME = "ddg-wireguard"
    }
}
