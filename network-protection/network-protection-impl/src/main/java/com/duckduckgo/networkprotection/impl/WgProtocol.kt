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
import com.duckduckgo.networkprotection.impl.config.PcapConfig
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.android.backend.GoBackend
import com.wireguard.crypto.Key
import dagger.SingleInstanceIn
import javax.inject.Inject
import javax.inject.Provider
import logcat.LogPriority.ERROR
import logcat.LogPriority.VERBOSE
import logcat.logcat

interface WgProtocol {
    fun startWg(
        tunFd: Int,
        configString: String,
        pcapConfig: PcapConfig? = null,
    ): Result<Unit>

    fun stopWg(): Result<Unit>
    fun getStatistics(): NetworkProtectionStatistics
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
        pcapConfig: PcapConfig?,
    ): Result<Unit> {
        return runCatching {
            safelyStartWg(tunFd, configString).also {
                safelyConfigurePcap(pcapConfig)
            }
        }.getOrDefault(Result.failure(java.lang.IllegalStateException("Wireguard failed to start")))
    }

    private fun safelyConfigurePcap(pcapConfig: PcapConfig?) {
        runCatching {
            pcapConfig?.let {
                goBackend.wgPcap(it.filename, it.snapLen, it.fileSize)
            } ?: goBackend.wgPcap(null, 0, 0)
        }
    }

    private fun safelyStartWg(
        tunFd: Int,
        configString: String,
    ): Result<Unit> {
        val level = if (appBuildConfig.isDebug) Log.VERBOSE else Log.ASSERT
        wgTunnel = goBackend.wgTurnOn(INTERFACE_NAME, tunFd, configString, level, appBuildConfig.sdkInt)
        return if (wgTunnel < 0) {
            logcat(ERROR) { "Wireguard tunnel failed to start: check config / tunFd" }
            Result.failure(java.lang.IllegalStateException("Wireguard failed to connect to backend"))
        } else {
            logcat { "Protecting V4 and V6 wg-sockets" }
            socketProtector.get().protect(goBackend.wgGetSocketV4(wgTunnel))
            socketProtector.get().protect(goBackend.wgGetSocketV6(wgTunnel))
            Result.success(Unit)
        }
    }

    override fun stopWg(): Result<Unit> {
        return runCatching {
            safeStopWg()
        }.getOrDefault(Result.failure(java.lang.IllegalStateException("Wireguard failed to stop")))
    }

    private fun safeStopWg(): Result<Unit> {
        if (wgTunnel != -1) {
            goBackend.wgTurnOff(wgTunnel)
            wgTunnel = -1
        }
        return Result.success(Unit)
    }

    override fun getStatistics(): NetworkProtectionStatistics =
        runCatching {
            goBackend.wgGetConfig(wgTunnel)?.toNetworkProtectionStatistics()
        }.getOrNull() ?: NetworkProtectionStatistics()

    private fun String.toNetworkProtectionStatistics(): NetworkProtectionStatistics {
        logcat { "Full config $this" }
        var rx = 0L
        var tx = 0L
        var serverIP = ""
        var publicKey = ""
        var lastHandshakeEpochSeconds = 0L
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
            } else if (it.startsWith("last_handshake_time_sec=")) {
                lastHandshakeEpochSeconds = it.substringAfter('=').toLong()
            }
        }
        return NetworkProtectionStatistics(
            publicKey = publicKey,
            serverIP = serverIP,
            receivedBytes = rx,
            transmittedBytes = tx,
            lastHandshakeEpochSeconds = lastHandshakeEpochSeconds,
        )
    }

    companion object {
        private const val INTERFACE_NAME = "ddg-wireguard"
    }
}
