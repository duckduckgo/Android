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
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.ExternalIdResult.ExternalId
import com.duckduckgo.subscriptions.impl.ExternalIdResult.Failure
import com.duckduckgo.subscriptions.impl.SubscriptionsViewModel.Command.ErrorMessage
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.NETHERLANDS_PLAN
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.UK_PLAN
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.YEARLY_PLAN
import com.duckduckgo.subscriptions.impl.repository.SubscriptionsRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class SubscriptionsViewModel @Inject constructor(
    private val billingClientWrapper: BillingClientWrapper,
    private val dispatcherProvider: DispatcherProvider,
    private val subscriptionsManager: SubscriptionsManager,
    subscriptionsRepository: SubscriptionsRepository,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

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
        subscriptionsManager.isSignedIn,
    ) { subscriptionDetails, offerDetails, hasSubscription, isSignedIn ->
        ViewState(
            hasSubscription = hasSubscription && isSignedIn,
            subscriptionDetails = subscriptionDetails,
            yearlySubscription = offerDetails[YEARLY_PLAN],
            monthlySubscription = offerDetails[MONTHLY_PLAN],
            ukSubscription = offerDetails[UK_PLAN],
            netherlandsSubscription = offerDetails[NETHERLANDS_PLAN],
        )
    }

    fun buySubscription(activity: Activity, productDetails: ProductDetails, offerToken: String, isReset: Boolean = false) {
        viewModelScope.launch(dispatcherProvider.io()) {
            when (val response = subscriptionsManager.getExternalId()) {
                is ExternalId -> {
                    val billingParams = billingFlowParamsBuilder(
                        productDetails = productDetails,
                        offerToken = offerToken,
                        externalId = response.id,
                        isReset = isReset,
                    ).build()
                    logcat(LogPriority.DEBUG) { "Subs: external id is ${response.id}" }
                    withContext(dispatcherProvider.main()) {
                        billingClientWrapper.launchBillingFlow(activity, billingParams)
                    }
                }
                is Failure -> {
                    logcat(LogPriority.ERROR) { "Subs: ${response.message}" }
                    sendCommand(ErrorMessage(response.message))
                }
            }
        }
    }

    fun recoverSubscription() {
        viewModelScope.launch(dispatcherProvider.io()) {
            when (val response = subscriptionsManager.getExternalId()) {
                is ExternalId -> {
                    logcat(LogPriority.DEBUG) { "Subs: external id is ${response.id}" }
                }
                is Failure -> {
                    logcat(LogPriority.ERROR) { "Subs: ${response.message}" }
                    sendCommand(ErrorMessage(response.message))
                }
            }
        }
    }

    private fun billingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
        isReset: Boolean,
    ): BillingFlowParams.Builder {
        val finalId = if (isReset) "randomId" else externalId
        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .setObfuscatedAccountId(finalId)
            .setObfuscatedProfileId(finalId)
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }
    sealed class Command {
        data class ErrorMessage(val message: String) : Command()
    }
}
