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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface SubscriptionsRepository {
    suspend fun subscriptionDetails(): ProductDetails?
    suspend fun offerDetail(): Map<String, SubscriptionOfferDetails>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSubscriptionsRepository @Inject constructor(
    private val billingClientWrapper: BillingClientWrapper,
) : SubscriptionsRepository {

    override suspend fun subscriptionDetails(): ProductDetails? {
        return billingClientWrapper.products.find { it.productId == BASIC_SUBSCRIPTION }
    }

    override suspend fun offerDetail(): Map<String, SubscriptionOfferDetails> {
        return subscriptionDetails()?.subscriptionOfferDetails?.associateBy { it.basePlanId }.orEmpty()
    }
}
