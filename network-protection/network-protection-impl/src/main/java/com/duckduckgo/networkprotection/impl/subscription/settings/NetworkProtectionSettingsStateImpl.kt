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

package com.duckduckgo.networkprotection.impl.subscription.settings

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.ACTIVE
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.EXPIRED
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.INACTIVE
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.INELIGIBLE
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.SIGNED_OUT
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.WAITING
import com.duckduckgo.networkprotection.impl.subscription.isActive
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState.Hidden
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState.Visible.Activating
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState.Visible.Expired
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState.Visible.Subscribed
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
class NetworkProtectionSettingsStateImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionState: NetworkProtectionState,
    private val netpSubscriptionManager: NetpSubscriptionManager,
) : NetworkProtectionSettingsState {

    override suspend fun getNetPSettingsStateFlow(): Flow<NetPSettingsState> =
        netpSubscriptionManager.vpnStatus().map { status ->
            if (!status.isActive()) {
                // if entitlement check succeeded and not an active subscription then reset state
                handleRevokedVPNState()
            }

            mapToSettingsState(status)
        }.flowOn(dispatcherProvider.io())

    private fun mapToSettingsState(vpnStatus: VpnStatus): NetPSettingsState = when (vpnStatus) {
        ACTIVE -> Subscribed
        INACTIVE, EXPIRED -> Expired
        WAITING -> Activating
        SIGNED_OUT, INELIGIBLE -> Hidden
    }

    private suspend fun handleRevokedVPNState() {
        if (networkProtectionState.isEnabled()) {
            networkProtectionState.stop()
        }
    }
}
