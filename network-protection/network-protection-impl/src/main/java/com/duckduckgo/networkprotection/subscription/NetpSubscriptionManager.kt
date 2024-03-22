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

package com.duckduckgo.networkprotection.subscription

import com.duckduckgo.networkprotection.subscription.NetpSubscriptionManager.VpnStatus
import com.duckduckgo.networkprotection.subscription.NetpSubscriptionManager.VpnStatus.ACTIVE
import com.duckduckgo.networkprotection.subscription.NetpSubscriptionManager.VpnStatus.EXPIRED
import kotlinx.coroutines.flow.Flow

// TODO this interface is moved here temporarily and its implementation remains in network-protection-subscription-internal
// to expedite Privacy Pro launch, we'll clean up later on
interface NetpSubscriptionManager {
    suspend fun getToken(): String?
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
    return this == ACTIVE
}

fun VpnStatus.isExpired(): Boolean {
    return this == EXPIRED
}
