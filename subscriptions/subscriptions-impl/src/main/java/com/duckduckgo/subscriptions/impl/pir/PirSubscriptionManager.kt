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

package com.duckduckgo.subscriptions.impl.pir

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.Product.PIR
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.pir.PirSubscriptionManager.PirStatus
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PirSubscriptionManager {
    fun pirStatus(): Flow<PirStatus>

    enum class PirStatus {
        ACTIVE,
        EXPIRED,
        SIGNED_OUT,
        INACTIVE,
        WAITING,
        INELIGIBLE,
    }
}

@ContributesBinding(AppScope::class)
class RealPirSubscriptionManager @Inject constructor(
    private val subscriptions: Subscriptions,
) : PirSubscriptionManager {

    override fun pirStatus(): Flow<PirStatus> = hasPirEntitlement().map { getPirStatusInternal(it) }

    private fun hasPirEntitlement(): Flow<Boolean> = subscriptions.getEntitlementStatus().map { it.contains(PIR) }

    private suspend fun getPirStatusInternal(hasValidEntitlement: Boolean): PirStatus = when {
        !hasValidEntitlement -> PirStatus.INELIGIBLE
        else -> when (subscriptions.getSubscriptionStatus()) {
            SubscriptionStatus.INACTIVE, SubscriptionStatus.EXPIRED -> PirStatus.EXPIRED
            SubscriptionStatus.UNKNOWN -> PirStatus.SIGNED_OUT
            SubscriptionStatus.AUTO_RENEWABLE, SubscriptionStatus.NOT_AUTO_RENEWABLE, SubscriptionStatus.GRACE_PERIOD -> PirStatus.ACTIVE
            SubscriptionStatus.WAITING -> PirStatus.WAITING
        }
    }
}
