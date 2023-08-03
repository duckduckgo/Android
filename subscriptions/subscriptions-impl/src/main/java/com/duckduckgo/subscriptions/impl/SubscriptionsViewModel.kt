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

package com.duckduckgo.subscriptions.impl

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.NETHERLANDS_PLAN
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.UK_PLAN
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.YEARLY_PLAN
import com.duckduckgo.subscriptions.impl.repository.SubscriptionsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.combine

@ContributesViewModel(ActivityScope::class)
class SubscriptionsViewModel @Inject constructor(
    private val billingClientWrapper: BillingClientWrapper,
    subscriptionsRepository: SubscriptionsRepository,
) : ViewModel() {

    data class ViewState(
        val hasSubscription: Boolean? = false,
        val subscriptionDetails: ProductDetails? = null,
        val yearlySubscription: SubscriptionOfferDetails? = null,
        val monthlySubscription: SubscriptionOfferDetails? = null,
        val ukSubscription: SubscriptionOfferDetails? = null,
        val netherlandsSubscription: SubscriptionOfferDetails? = null,
    )

    val subscriptionsFlow = combine(
        subscriptionsRepository.subscriptionDetails,
        subscriptionsRepository.offerDetails,
        subscriptionsRepository.hasSubscription,
    ) { subscriptionDetails, offerDetails, hasSubscription ->
        ViewState(
            hasSubscription = hasSubscription,
            subscriptionDetails = subscriptionDetails,
            yearlySubscription = offerDetails[YEARLY_PLAN],
            monthlySubscription = offerDetails[MONTHLY_PLAN],
            ukSubscription = offerDetails[UK_PLAN],
            netherlandsSubscription = offerDetails[NETHERLANDS_PLAN],
        )
    }

    fun buySubscription(activity: Activity, productDetails: ProductDetails, offerToken: String) {
        val billingParams = billingFlowParamsBuilder(productDetails = productDetails, offerToken = offerToken).build()
        billingClientWrapper.launchBillingFlow(activity, billingParams)
    }

    private fun billingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String = "",
    ): BillingFlowParams.Builder {
        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .setObfuscatedAccountId("accountIdTest")
            .setObfuscatedProfileId("profileIdTest")
    }
}
