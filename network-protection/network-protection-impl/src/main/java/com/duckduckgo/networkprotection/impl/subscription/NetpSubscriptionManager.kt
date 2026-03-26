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

package com.duckduckgo.networkprotection.impl.subscription

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NetpSubscriptionManager {
    suspend fun getVpnStatus(): VpnStatus
    suspend fun vpnStatus(): Flow<VpnStatus>
    enum class VpnStatus {
        ACTIVE,
        EXPIRED,
        SIGNED_OUT,
        INACTIVE,
    }
}

fun VpnStatus.isActive(): Boolean {
    return this == VpnStatus.ACTIVE
}

fun VpnStatus.isExpired(): Boolean {
    return this == VpnStatus.EXPIRED
}

@ContributesBinding(AppScope::class)
class RealNetpSubscriptionManager @Inject constructor(
    private val subscriptions: Subscriptions,
    private val dispatcherProvider: DispatcherProvider,
) : NetpSubscriptionManager {

    override suspend fun getVpnStatus(): VpnStatus {
        val hasValidEntitlement = hasValidEntitlement()
        return getVpnStatusInternal(hasValidEntitlement)
    }

    override suspend fun vpnStatus(): Flow<VpnStatus> {
        return hasValidEntitlementFlow().map { getVpnStatusInternal(it) }
    }

    private suspend fun hasValidEntitlement(): Boolean = withContext(dispatcherProvider.io()) {
        val entitlements = subscriptions.getEntitlementStatus().firstOrNull()
        return@withContext (entitlements?.contains(NetP) == true)
    }

    private fun hasValidEntitlementFlow(): Flow<Boolean> = subscriptions.getEntitlementStatus().map { it.contains(NetP) }

    private suspend fun getVpnStatusInternal(hasValidEntitlement: Boolean): VpnStatus {
        val subscriptionState = subscriptions.getSubscriptionStatus()
        return when (subscriptionState) {
            SubscriptionStatus.INACTIVE, SubscriptionStatus.EXPIRED -> VpnStatus.EXPIRED
            SubscriptionStatus.UNKNOWN -> VpnStatus.SIGNED_OUT
            else -> {
                if (hasValidEntitlement) {
                    VpnStatus.ACTIVE
                } else {
                    VpnStatus.INACTIVE
                }
            }
        }
    }
}
