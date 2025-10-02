/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.state

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLING
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTING
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.cohort.NetpCohortStore
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.asServerDetails
import com.duckduckgo.networkprotection.impl.exclusion.NetPExclusionListRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class NetworkProtectionStateImpl @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val cohortStore: NetpCohortStore,
    private val dispatcherProvider: DispatcherProvider,
    private val wgTunnelConfig: WgTunnelConfig,
    private val vpnStateMonitor: VpnStateMonitor,
    private val netPExclusionListRepository: NetPExclusionListRepository,
) : NetworkProtectionState {
    override suspend fun isOnboarded(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext cohortStore.cohortLocalDate != null
    }

    override suspend fun isEnabled(): Boolean {
        return vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)
    }

    override suspend fun isRunning(): Boolean {
        return vpnFeaturesRegistry.isFeatureRunning(NetPVpnFeature.NETP_VPN)
    }

    override fun start() {
        coroutineScope.launch(dispatcherProvider.io()) {
            vpnFeaturesRegistry.registerFeature(NetPVpnFeature.NETP_VPN)
        }
    }

    override fun restart() {
        coroutineScope.launch(dispatcherProvider.io()) {
            vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
        }
    }

    override fun clearVPNConfigurationAndRestart() {
        coroutineScope.launch(dispatcherProvider.io()) {
            wgTunnelConfig.clearWgConfig()
            restart()
        }
    }

    override suspend fun stop() {
        vpnFeaturesRegistry.unregisterFeature(NetPVpnFeature.NETP_VPN)
    }

    override fun clearVPNConfigurationAndStop() {
        coroutineScope.launch(dispatcherProvider.io()) {
            wgTunnelConfig.clearWgConfig()
            stop()
        }
    }

    override fun serverLocation(): String? {
        return runBlocking { wgTunnelConfig.getWgConfig() }?.asServerDetails()?.location
    }

    override fun getConnectionStateFlow(): Flow<ConnectionState> {
        return vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN).map {
            when (it.state) {
                ENABLED -> CONNECTED
                ENABLING -> CONNECTING
                else -> DISCONNECTED
            }
        }
    }

    override suspend fun getExcludedApps(): List<String> = withContext(dispatcherProvider.io()) {
        netPExclusionListRepository.getExcludedAppPackages()
    }
}
