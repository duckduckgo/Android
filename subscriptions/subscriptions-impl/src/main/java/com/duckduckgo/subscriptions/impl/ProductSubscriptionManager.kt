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

package com.duckduckgo.subscriptions.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface ProductSubscriptionManager {

    fun entitlementStatus(vararg products: Product): Flow<ProductStatus>

    enum class ProductStatus {
        ACTIVE,
        EXPIRED,
        SIGNED_OUT,
        INACTIVE,
        WAITING,
        INELIGIBLE,
    }
}

@ContributesBinding(AppScope::class)
class RealProductSubscriptionManager @Inject constructor(
    private val subscriptions: Subscriptions,
) : ProductSubscriptionManager {

    override fun entitlementStatus(vararg products: Product): Flow<ProductStatus> =
        hasEntitlement(*products).map { getEntitlementStatusInternal(it) }

    private fun hasEntitlement(vararg products: Product): Flow<Boolean> =
        subscriptions.getEntitlementStatus().map { entitledProducts -> entitledProducts.any { products.contains(it) } }

    private suspend fun getEntitlementStatusInternal(hasValidEntitlement: Boolean): ProductStatus = when {
        !hasValidEntitlement -> ProductStatus.INELIGIBLE
        else -> when (subscriptions.getSubscriptionStatus()) {
            SubscriptionStatus.INACTIVE, SubscriptionStatus.EXPIRED -> ProductStatus.EXPIRED
            SubscriptionStatus.UNKNOWN -> ProductStatus.SIGNED_OUT
            SubscriptionStatus.AUTO_RENEWABLE, SubscriptionStatus.NOT_AUTO_RENEWABLE, SubscriptionStatus.GRACE_PERIOD -> ProductStatus.ACTIVE
            SubscriptionStatus.WAITING -> ProductStatus.WAITING
        }
    }
}
