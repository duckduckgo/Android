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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.subscriptions.impl.CurrentPurchase
import com.duckduckgo.subscriptions.impl.JSONObjectAdapter
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ITR
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.NETP
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PIR
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PLATFORM
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.billing.getPrice
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.SubscriptionsRepository
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.*
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Failure
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.InProgress
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Inactive
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Recovered
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Success
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

@ContributesViewModel(ActivityScope::class)
class SubscriptionWebViewViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val subscriptionsManager: SubscriptionsManager,
    private val subscriptionsRepository: SubscriptionsRepository,
    private val networkProtectionWaitlist: NetworkProtectionWaitlist,
    private val pixelSender: SubscriptionPixelSender,
) : ViewModel() {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val jsonAdapter: JsonAdapter<SubscriptionOptionsJson> = moshi.adapter(SubscriptionOptionsJson::class.java)

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    data class CurrentPurchaseViewState(val purchaseState: PurchaseStateView = Inactive)

    private val _currentPurchaseViewState = MutableStateFlow(CurrentPurchaseViewState())
    val currentPurchaseViewState = _currentPurchaseViewState.asStateFlow()

    fun start() {
        subscriptionsManager.currentPurchaseState.onEach {
            val state = when (it) {
                is CurrentPurchase.Failure -> Failure(it.message)
                is CurrentPurchase.Success -> Success(
                    SubscriptionEventData(
                        PURCHASE_COMPLETED_FEATURE_NAME,
                        PURCHASE_COMPLETED_SUBSCRIPTION_NAME,
                        JSONObject(PURCHASE_COMPLETED_JSON),
                    ),
                )
                is CurrentPurchase.InProgress, CurrentPurchase.PreFlowInProgress -> InProgress
                is CurrentPurchase.Recovered -> Recovered
                is CurrentPurchase.PreFlowFinished -> Inactive
            }
            _currentPurchaseViewState.emit(currentPurchaseViewState.value.copy(purchaseState = state))
        }.flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    fun processJsCallbackMessage(featureName: String, method: String, id: String?, data: JSONObject?) {
        when (method) {
            "backToSettings" -> backToSettings()
            "getSubscriptionOptions" -> id?.let { getSubscriptionOptions(featureName, method, it) }
            "subscriptionSelected" -> subscriptionSelected(data)
            "activateSubscription" -> activateSubscription()
            "featureSelected" -> data?.let { featureSelected(data) }
            else -> {
                // NOOP
            }
        }
    }

    private fun featureSelected(data: JSONObject) {
        val feature = runCatching { data.getString("feature") }.getOrNull() ?: return
        viewModelScope.launch {
            val commandToSend = when (feature) {
                NETP -> GoToNetP(networkProtectionWaitlist.getScreenForCurrentState())
                ITR -> GoToITR
                PIR -> GoToPIR
                else -> null
            }
            when (commandToSend) {
                GoToITR -> pixelSender.reportOnboardingIdtrClick()
                is GoToNetP -> pixelSender.reportOnboardingVpnClick()
                GoToPIR -> pixelSender.reportOnboardingPirClick()
                else -> {} // no-op
            }
            commandToSend?.let {
                command.send(commandToSend)
            }
        }
    }
    private fun activateSubscription() {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (subscriptionsManager.hasSubscription()) {
                pixelSender.reportOnboardingAddDeviceClick()
                activateOnAnotherDevice()
            } else {
                pixelSender.reportOfferRestorePurchaseClick()
                recoverSubscription()
            }
        }
    }

    private fun subscriptionSelected(data: JSONObject?) {
        pixelSender.reportOfferSubscribeClick()

        viewModelScope.launch(dispatcherProvider.io()) {
            val id = runCatching { data?.getString("id") }.getOrNull()
            if (id.isNullOrBlank()) {
                pixelSender.reportPurchaseFailureOther()
                _currentPurchaseViewState.emit(currentPurchaseViewState.value.copy(purchaseState = Failure("")))
            } else {
                command.send(SubscriptionSelected(id))
            }
        }
    }

    fun purchaseSubscription(activity: Activity, id: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val offerToken = runCatching { subscriptionsRepository.offerDetail()[id]?.offerToken }.getOrNull() ?: return@launch
            val productDetails = subscriptionsRepository.subscriptionDetails() ?: return@launch
            subscriptionsManager.purchase(activity, productDetails, offerToken, false)
        }
    }

    private fun getSubscriptionOptions(featureName: String, method: String, id: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val yearly = subscriptionsRepository.offerDetail()[YEARLY_PLAN]
            val monthly = subscriptionsRepository.offerDetail()[MONTHLY_PLAN]

            val yearlyJson = OptionsJson(
                id = yearly?.basePlanId!!,
                cost = CostJson(displayPrice = yearly.getPrice(), recurrence = YEARLY),
            )

            val monthlyJson = OptionsJson(
                id = monthly?.basePlanId!!,
                cost = CostJson(displayPrice = monthly.getPrice(), recurrence = MONTHLY),
            )

            val subscriptionOptions = jsonAdapter.toJson(
                SubscriptionOptionsJson(
                    options = listOf(yearlyJson, monthlyJson),
                    features = listOf(FeatureJson(NETP), FeatureJson(ITR), FeatureJson(PIR)),
                ),
            )

            val response = JsCallbackData(
                featureName = featureName,
                method = method,
                id = id,
                params = JSONObject(subscriptionOptions),
            )
            command.send(SendResponseToJs(response))
        }
    }

    private fun recoverSubscription() {
        viewModelScope.launch {
            command.send(RestoreSubscription)
        }
    }

    private fun activateOnAnotherDevice() {
        viewModelScope.launch {
            command.send(ActivateOnAnotherDevice)
        }
    }

    private fun backToSettings() {
        viewModelScope.launch {
            command.send(BackToSettings)
        }
    }

    data class SubscriptionOptionsJson(
        val platform: String = PLATFORM,
        val options: List<OptionsJson>,
        val features: List<FeatureJson>,
    )

    data class OptionsJson(
        val id: String,
        val cost: CostJson,
    )

    data class CostJson(val displayPrice: String, val recurrence: String)
    data class FeatureJson(val name: String)

    sealed class PurchaseStateView {
        data object Inactive : PurchaseStateView()
        data object InProgress : PurchaseStateView()
        data class Success(val subscriptionEventData: SubscriptionEventData) : PurchaseStateView()
        data object Recovered : PurchaseStateView()
        data class Failure(val message: String) : PurchaseStateView()
    }

    sealed class Command {
        data object BackToSettings : Command()
        data class SendResponseToJs(val data: JsCallbackData) : Command()
        data class SubscriptionSelected(val id: String) : Command()
        data object ActivateOnAnotherDevice : Command()
        data object RestoreSubscription : Command()
        data object GoToITR : Command()
        data object GoToPIR : Command()
        data class GoToNetP(val activityParams: ActivityParams) : Command()
    }

    companion object {
        const val PURCHASE_COMPLETED_FEATURE_NAME = "useSubscription"
        const val PURCHASE_COMPLETED_SUBSCRIPTION_NAME = "onPurchaseUpdate"
        const val PURCHASE_COMPLETED_JSON = """{ type: "completed" }"""
    }
}
