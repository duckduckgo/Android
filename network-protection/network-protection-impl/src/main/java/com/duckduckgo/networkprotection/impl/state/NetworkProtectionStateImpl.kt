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

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.cohort.NetpCohortStore
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
class NetworkProtectionStateImpl @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val coroutineScope: CoroutineScope,
    private val cohortStore: NetpCohortStore,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionRepository: NetworkProtectionRepository,
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

    override fun restart() {
        coroutineScope.launch {
            vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
        }
    }

    override fun serverLocation(): String? {
        return networkProtectionRepository.serverDetails?.location
    }
}
