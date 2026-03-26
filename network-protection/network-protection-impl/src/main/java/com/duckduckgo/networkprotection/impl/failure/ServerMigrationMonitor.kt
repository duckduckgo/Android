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
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.WgVpnControllerService
import com.duckduckgo.networkprotection.impl.configuration.asServerDetails
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@ContributesMultibinding(VpnScope::class)
class ServerMigrationMonitor @Inject constructor(
    private val wgVpnControllerService: WgVpnControllerService,
    private val wgTunnelConfig: WgTunnelConfig,
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionPixels: NetworkProtectionPixels,
) : VpnServiceCallbacks {
    private val job = ConflatedJob()
    private var migrating = false

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            startMonitor()
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            if (migrating) {
                // onVpnReconfigured is called with migrating true means that VPN was restarted due to migration
                migrating = false
                networkProtectionPixels.reportServerMigrationAttemptSuccess()
            }
            startMonitor()
        }
    }

    override fun onVpnStartFailed(coroutineScope: CoroutineScope) {
        super.onVpnStartFailed(coroutineScope)
        if (migrating) {
            // onVpnStartFailed is called with migrating true means that VPN failed to be restarted when attempting to migrate the user
            // It will most likely fail on unable to get config since we cleared it prior
            migrating = false
            networkProtectionPixels.reportServerMigrationAttemptFailed()
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        // clear since VPN is not running anymore and migration is irrelevant
        migrating = false
        stopMonitor()
    }

    private suspend fun startMonitor() = withContext(dispatcherProvider.io()) {
        if (networkProtectionState.isEnabled()) {
            while (isActive && networkProtectionState.isEnabled()) {
                delay(5.minutes.inWholeMilliseconds)

                val serverName = wgTunnelConfig.getWgConfig()?.asServerDetails()?.serverName
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

    private fun attemptServerMigration() {
        migrating = true
        wgTunnelConfig.clearWgConfig()
        // This should cause for the VPN to be reconfigured
        networkProtectionState.restart()
    }

    private fun stopMonitor() {
        job.cancel()
    }
}
