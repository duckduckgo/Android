/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.failure

import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.WgVpnControllerService
import com.duckduckgo.networkprotection.impl.configuration.asServerDetails
import com.duckduckgo.networkprotection.impl.di.ProtectedVpnControllerService
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import com.wireguard.crypto.KeyPair
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@ContributesMultibinding(VpnScope::class)
class ServerMigrationMonitor @Inject constructor(
    @ProtectedVpnControllerService private val wgVpnControllerService: WgVpnControllerService,
    private val wgTunnelConfig: WgTunnelConfig,
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
    private val wgTunnel: WgTunnel,
    private val networkProtectionPixels: NetworkProtectionPixels,
) : VpnServiceCallbacks {
    private val job = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            startMonitor()
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            startMonitor()
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        stopMonitor()
    }

    private suspend fun startMonitor() = withContext(dispatcherProvider.io()) {
        if (networkProtectionState.isEnabled()) {
            val serverName = wgTunnelConfig.getWgConfig()?.asServerDetails()?.serverName
            logcat { "Server drain monitor: monitor started for $serverName" }
            while (isActive && networkProtectionState.isEnabled()) {
                delay(5.minutes.inWholeMilliseconds)

                if (serverName != null) {
                    kotlin.runCatching {
                        logcat { "Server drain monitor: getServerStatus for $serverName" }
                        wgVpnControllerService.getServerStatus(serverName).also {
                            if (it.shouldMigrate) {
                                networkProtectionPixels.reportServerMigrationAttempt()
                                logcat { "Server drain monitor: attempting to migrate server" }
                                attemptServerMigration()
                            }
                        }
                    }.onFailure {
                        logcat { "Server drain monitor: getServerStatus error $it" }
                    }
                }
            }
        } else {
            job.cancel()
        }
    }

    private suspend fun attemptServerMigration() {
        kotlin.runCatching {
            val config = wgTunnel.createAndSetWgConfig(KeyPair())
            networkProtectionState.restart()
            config.getOrNull()
        }.onFailure {
            networkProtectionPixels.reportServerMigrationAttemptFailed()
            logcat { "Server drain monitor: server migration failed" }
        }.onSuccess {
            networkProtectionPixels.reportServerMigrationAttemptSuccess()
            logcat { "Server drain monitor: server migration succeeded to ${it?.asServerDetails()?.serverName}}" }
        }
    }

    private fun stopMonitor() {
        job.cancel()
    }
}
