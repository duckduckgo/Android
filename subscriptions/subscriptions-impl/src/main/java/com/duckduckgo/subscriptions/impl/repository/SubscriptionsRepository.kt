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
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.android.billingclient.api.Purchase
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.BASIC_SUBSCRIPTION
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface SubscriptionsRepository {
    val hasSubscription: Flow<Boolean>
    val subscriptionDetails: Flow<ProductDetails>
    val offerDetails: StateFlow<Map<String, SubscriptionOfferDetails>>
    val purchases: Flow<List<Purchase>>
}

@ContributesBinding(AppScope::class)
class RealSubscriptionsRepository @Inject constructor(
    billingClientWrapper: BillingClientWrapper,
    dispatchers: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : SubscriptionsRepository {

    private val _offerDetails = MutableStateFlow<Map<String, SubscriptionOfferDetails>>(emptyMap())
    override val offerDetails = _offerDetails.asStateFlow()

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

    init {
        coroutineScope.launch(dispatchers.io()) {
            subscriptionDetails.collect { productDetails ->
                val offersMap = productDetails.subscriptionOfferDetails?.associateBy { it.basePlanId }.orEmpty()
                _offerDetails.emit(offersMap)
            }
        }
    }
}
