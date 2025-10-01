/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.menu

import com.duckduckgo.app.browser.viewstate.VpnMenuState
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

interface VpnMenuStateProvider {
    fun getVpnMenuState(): Flow<VpnMenuState>
}

@ContributesBinding(AppScope::class)
class VpnMenuStateProviderImpl @Inject constructor(
    private val subscriptions: Subscriptions,
    private val networkProtectionState: NetworkProtectionState,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val vpnMenuStore: VpnMenuStore,
) : VpnMenuStateProvider {

    override fun getVpnMenuState(): Flow<VpnMenuState> {
        return combine(
            subscriptions.getSubscriptionStatusFlow(),
            subscriptions.getEntitlementStatus(),
            networkProtectionState.getConnectionStateFlow(),
        ) { subscriptionStatus, entitlements, connectionState ->
            if (!androidBrowserConfigFeature.vpnMenuItem().isEnabled()) {
                return@combine VpnMenuState.Hidden
            }

            when {
                subscriptionStatus.isActive() && entitlements.contains(NetP) -> {
                    VpnMenuState.Subscribed(isVpnEnabled = connectionState.isConnected())
                }
                // User has subscription but no NetP entitlement
                subscriptionStatus.isActive() -> VpnMenuState.Hidden
                else -> {
                    if (vpnMenuStore.canShowVpnMenuForNotSubscribed()) {
                        VpnMenuState.NotSubscribed
                    } else {
                        VpnMenuState.Hidden
                    }
                }
            }
        }
    }
}

private fun SubscriptionStatus.isActive(): Boolean {
    return when (this) {
        SubscriptionStatus.AUTO_RENEWABLE,
        SubscriptionStatus.NOT_AUTO_RENEWABLE,
        SubscriptionStatus.GRACE_PERIOD,
        -> true
        else -> false
    }
}
