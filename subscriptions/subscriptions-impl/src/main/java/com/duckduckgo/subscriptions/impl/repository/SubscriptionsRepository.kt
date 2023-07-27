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

package com.duckduckgo.subscriptions.impl.repository

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.BASIC_SUBSCRIPTION
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

interface SubscriptionsRepository {
    val hasSubscription: Flow<Boolean>
    val subscriptionDetails: Flow<ProductDetails>
    val purchases: Flow<List<Purchase>>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSubscriptionsRepository @Inject constructor(billingClientWrapper: BillingClientWrapper) : SubscriptionsRepository {

    override val hasSubscription: Flow<Boolean> = billingClientWrapper.purchases.map { purchaseList ->
        purchaseList.any { purchase ->
            purchase.products.contains(BASIC_SUBSCRIPTION)
        }
    }

    override val subscriptionDetails: Flow<ProductDetails> =
        billingClientWrapper.products.filter {
            it.containsKey(
                BASIC_SUBSCRIPTION,
            )
        }.map { it[BASIC_SUBSCRIPTION]!! }

    override val purchases: Flow<List<Purchase>> = billingClientWrapper.purchases
}
