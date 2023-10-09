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
import com.duckduckgo.subscriptions.impl.SubscriptionsData.Failure
import com.duckduckgo.subscriptions.impl.SubscriptionsData.Success
import com.duckduckgo.subscriptions.impl.SubscriptionsViewModel.Command.ErrorMessage
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.YEARLY_PLAN
import com.duckduckgo.subscriptions.impl.repository.SubscriptionsRepository
import com.duckduckgo.subscriptions.store.AuthDataStore
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val subscriptionsRepository: SubscriptionsRepository,
    private val authDataStore: AuthDataStore,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    data class ViewState(
        val isUserAuthenticated: Boolean? = false,
        val hasSubscription: Boolean? = false,
        val subscriptionDetails: ProductDetails? = null,
        val yearlySubscription: SubscriptionOfferDetails? = null,
        val monthlySubscription: SubscriptionOfferDetails? = null,
    )

    fun start() {
        viewModelScope.launch(dispatcherProvider.io()) {
            subscriptionsManager.isSignedIn.collect {
                viewState.emit(
                    viewState.value.copy(
                        isUserAuthenticated = it,
                        hasSubscription = subscriptionsManager.hasSubscription(),
                        subscriptionDetails = subscriptionsRepository.subscriptionDetails(),
                        yearlySubscription = subscriptionsRepository.offerDetail()[YEARLY_PLAN],
                        monthlySubscription = subscriptionsRepository.offerDetail()[MONTHLY_PLAN],
                    ),
                )
            }
        }

        viewModelScope.launch(dispatcherProvider.io()) {
            billingClientWrapper.purchases.collect {
                it.lastOrNull()?.let {
                    viewState.emit(
                        viewState.value.copy(
                            hasSubscription = subscriptionsManager.hasSubscription(),
                        ),
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(dispatcherProvider.io()) {
            subscriptionsManager.signOut()
        }
    }

    fun buySubscription(activity: Activity, productDetails: ProductDetails, offerToken: String, isReset: Boolean = false) {
        viewModelScope.launch(dispatcherProvider.io()) {
            when (val response = subscriptionsManager.prePurchaseFlow()) {
                is Success -> {
                    val billingParams = billingFlowParamsBuilder(
                        productDetails = productDetails,
                        offerToken = offerToken,
                        externalId = response.externalId,
                        isReset = isReset,
                    ).build()
                    logcat(LogPriority.DEBUG) { "Subs: external id is ${response.externalId}" }
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
            when (val response = subscriptionsManager.recoverSubscriptionFromStore()) {
                is Success -> {
                    logcat(LogPriority.DEBUG) { "Subs: external id is ${response.externalId}" }
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
