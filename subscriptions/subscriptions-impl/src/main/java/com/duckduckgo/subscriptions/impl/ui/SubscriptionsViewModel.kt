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

package com.duckduckgo.subscriptions.impl.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.CurrentPurchase
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.YEARLY_PLAN
import com.duckduckgo.subscriptions.impl.repository.SubscriptionsRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SubscriptionsViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val subscriptionsManager: SubscriptionsManager,
    private val subscriptionsRepository: SubscriptionsRepository,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val isUserAuthenticated: Boolean? = false,
        val hasSubscription: Boolean? = false,
        val subscriptionDetails: ProductDetails? = null,
        val yearlySubscription: SubscriptionOfferDetails? = null,
        val monthlySubscription: SubscriptionOfferDetails? = null,
    )

    data class CurrentPurchaseViewState(val purchaseState: PurchaseStateView = PurchaseStateView.Inactive)

    val viewState = combine(
        subscriptionsManager.isSignedIn,
        subscriptionsManager.hasSubscription,
    ) { isSignedIn, hasSubscription ->
        ViewState(
            isUserAuthenticated = isSignedIn,
            hasSubscription = hasSubscription,
            subscriptionDetails = subscriptionsRepository.subscriptionDetails(),
            yearlySubscription = subscriptionsRepository.offerDetail()[YEARLY_PLAN],
            monthlySubscription = subscriptionsRepository.offerDetail()[MONTHLY_PLAN],
        )
    }

    private val _currentPurchaseViewState = MutableStateFlow(CurrentPurchaseViewState())
    val currentPurchaseViewState = _currentPurchaseViewState.asStateFlow()

    fun start() {
        viewModelScope.launch {
            subscriptionsManager.currentPurchaseState.collect {
                val state = when (it) {
                    is CurrentPurchase.Failure -> PurchaseStateView.Failure(it.message)
                    is CurrentPurchase.Success -> PurchaseStateView.Success
                    is CurrentPurchase.InProgress -> PurchaseStateView.InProgress
                    is CurrentPurchase.Recovered -> PurchaseStateView.Recovered
                }
                _currentPurchaseViewState.emit(currentPurchaseViewState.value.copy(purchaseState = state))
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
            subscriptionsManager.purchase(activity, productDetails, offerToken, isReset)
        }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }

    sealed class PurchaseStateView {
        object Inactive : PurchaseStateView()
        object InProgress : PurchaseStateView()
        object Success : PurchaseStateView()
        object Recovered : PurchaseStateView()
        data class Failure(val message: String) : PurchaseStateView()
    }

    sealed class Command {
        data class ErrorMessage(val message: String) : Command()
    }
}
