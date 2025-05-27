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
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.CurrentPurchase
import com.duckduckgo.subscriptions.impl.JSONObjectAdapter
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionOffer
import com.duckduckgo.subscriptions.impl.SubscriptionsChecker
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ITR
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LEGACY_FE_ITR
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LEGACY_FE_NETP
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LEGACY_FE_PIR
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_OF_FREE_TRIAL_OFFERS
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_FREE_TRIAL_OFFER_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_FREE_TRIAL_OFFER_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.NETP
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PIR
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PLATFORM
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ROW_ITR
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_FREE_TRIAL_OFFER_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_FREE_TRIAL_OFFER_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.isActive
import com.duckduckgo.subscriptions.impl.repository.isExpired
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.*
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Failure
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.InProgress
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Inactive
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Recovered
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Success
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Waiting
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
    private val subscriptionsChecker: SubscriptionsChecker,
    private val networkProtectionAccessState: NetworkProtectionAccessState,
    private val pixelSender: SubscriptionPixelSender,
    private val privacyProFeature: PrivacyProFeature,
) : ViewModel() {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val jsonAdapter: JsonAdapter<SubscriptionOptionsJson> = moshi.adapter(SubscriptionOptionsJson::class.java)

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    data class CurrentPurchaseViewState(val purchaseState: PurchaseStateView = Inactive)

    private val _currentPurchaseViewState = MutableStateFlow(CurrentPurchaseViewState())
    val currentPurchaseViewState = _currentPurchaseViewState.asStateFlow()

    private lateinit var subscriptionStatus: SubscriptionStatus

    fun start() {
        subscriptionsManager.currentPurchaseState.onEach {
            val state = when (it) {
                is CurrentPurchase.Canceled -> {
                    enablePurchaseButton()
                    Inactive
                }
                is CurrentPurchase.Failure -> {
                    enablePurchaseButton()
                    Failure
                }
                is CurrentPurchase.Waiting -> {
                    subscriptionsChecker.runChecker()
                    Waiting
                }
                is CurrentPurchase.Success -> {
                    subscriptionsChecker.runChecker()
                    Success(
                        SubscriptionEventData(
                            PURCHASE_COMPLETED_FEATURE_NAME,
                            PURCHASE_COMPLETED_SUBSCRIPTION_NAME,
                            JSONObject(PURCHASE_COMPLETED_JSON),
                        ),
                    )
                }
                is CurrentPurchase.InProgress, CurrentPurchase.PreFlowInProgress -> InProgress
                is CurrentPurchase.Recovered -> Recovered
                is CurrentPurchase.PreFlowFinished -> Inactive
            }
            _currentPurchaseViewState.emit(currentPurchaseViewState.value.copy(purchaseState = state))
        }.flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)

        subscriptionsManager.subscriptionStatus
            .onEach { subscriptionStatus = it }
            .launchIn(viewModelScope)
    }

    fun processJsCallbackMessage(featureName: String, method: String, id: String?, data: JSONObject?) {
        when (method) {
            "backToSettings" -> backToSettings()
            "backToSettingsActivateSuccess" -> backToSettingsActiveSuccess()
            "getSubscriptionOptions" -> id?.let { getSubscriptionOptions(featureName, method, it) }
            "subscriptionSelected" -> subscriptionSelected(data)
            "activateSubscription" -> activateSubscription()
            "featureSelected" -> data?.let { featureSelected(data) }
            "subscriptionsWelcomeFaqClicked" -> subscriptionsWelcomeFaqClicked()
            "subscriptionsWelcomeAddEmailClicked" -> subscriptionsWelcomeAddEmailClicked()
            else -> {
                // NOOP
            }
        }
    }

    fun onSubscriptionRestored() = viewModelScope.launch {
        if (subscriptionStatus.isExpired()) {
            command.send(BackToSettings)
        } else {
            command.send(Reload)
        }
    }

    private fun subscriptionsWelcomeFaqClicked() {
        if (hasPurchasedSubscription()) {
            pixelSender.reportOnboardingFaqClick()
        }
    }

    private fun subscriptionsWelcomeAddEmailClicked() {
        if (hasPurchasedSubscription()) {
            pixelSender.reportOnboardingAddDeviceClick()
        }
    }

    private fun enablePurchaseButton() {
        viewModelScope.launch {
            val response = SubscriptionEventData(
                featureName = PURCHASE_COMPLETED_FEATURE_NAME,
                subscriptionName = PURCHASE_COMPLETED_SUBSCRIPTION_NAME,
                params = JSONObject(PURCHASE_CANCELED_JSON),
            )
            command.send(SendJsEvent(response))
        }
    }

    private fun featureSelected(data: JSONObject) {
        val feature = runCatching { data.getString("feature") }.getOrNull() ?: return
        viewModelScope.launch {
            val commandToSend = when (feature) {
                NETP, LEGACY_FE_NETP -> networkProtectionAccessState.getScreenForCurrentState()?.let { GoToNetP(it) }
                ITR, LEGACY_FE_ITR, ROW_ITR -> GoToITR
                PIR, LEGACY_FE_PIR -> GoToPIR
                else -> null
            }
            if (hasPurchasedSubscription()) {
                when (commandToSend) {
                    GoToITR -> pixelSender.reportOnboardingIdtrClick()
                    is GoToNetP -> pixelSender.reportOnboardingVpnClick()
                    GoToPIR -> pixelSender.reportOnboardingPirClick()
                    else -> {} // no-op
                }
            }
            commandToSend?.let {
                command.send(commandToSend)
            }
        }
    }
    private fun activateSubscription() {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (!subscriptionsManager.subscriptionStatus().isActive()) {
                pixelSender.reportOfferRestorePurchaseClick()
                recoverSubscription()
            }
        }
    }

    private fun hasPurchasedSubscription() = currentPurchaseViewState.value.purchaseState is Success

    private fun subscriptionSelected(data: JSONObject?) {
        pixelSender.reportOfferSubscribeClick()

        viewModelScope.launch(dispatcherProvider.io()) {
            val id = runCatching { data?.getString("id") }.getOrNull()
            val offerId = runCatching { data?.getString("offerId") }.getOrNull()
            val experimentName = runCatching { data?.getJSONObject("experiment")?.getString("name") }.getOrNull()
            val experimentCohort = runCatching { data?.getJSONObject("experiment")?.getString("cohort") }.getOrNull()
            if (id.isNullOrBlank()) {
                pixelSender.reportPurchaseFailureOther()
                _currentPurchaseViewState.emit(currentPurchaseViewState.value.copy(purchaseState = Failure))
            } else {
                command.send(SubscriptionSelected(id, offerId, experimentName, experimentCohort))
            }
        }
    }

    fun purchaseSubscription(
        activity: Activity,
        planId: String,
        offerId: String?,
        experimentName: String?,
        experimentCohort: String?,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            subscriptionsManager.purchase(activity, planId, offerId, experimentName, experimentCohort)
        }
    }

    private fun getSubscriptionOptions(featureName: String, method: String, id: String) {
        suspend fun sendOptionJson(optionsJson: SubscriptionOptionsJson) {
            val response = JsCallbackData(
                featureName = featureName,
                method = method,
                id = id,
                params = JSONObject(jsonAdapter.toJson(optionsJson)),
            )
            command.send(SendResponseToJs(response))
        }

        viewModelScope.launch(dispatcherProvider.io()) {
            val defaultOptions = SubscriptionOptionsJson(
                options = emptyList(),
                features = emptyList(),
            )

            val subscriptionOptions = if (privacyProFeature.allowPurchase().isEnabled()) {
                val subscriptionOffers = subscriptionsManager.getSubscriptionOffer().associateBy { it.offerId ?: it.planId }
                when {
                    subscriptionOffers.keys.containsAll(listOf(MONTHLY_FREE_TRIAL_OFFER_US, YEARLY_FREE_TRIAL_OFFER_US)) && isFreeTrialEligible() -> {
                        createSubscriptionOptions(
                            monthlyOffer = subscriptionOffers.getValue(MONTHLY_FREE_TRIAL_OFFER_US),
                            yearlyOffer = subscriptionOffers.getValue(YEARLY_FREE_TRIAL_OFFER_US),
                        )
                    }

                    subscriptionOffers.keys.containsAll(listOf(MONTHLY_FREE_TRIAL_OFFER_ROW, YEARLY_FREE_TRIAL_OFFER_ROW)) &&
                        isFreeTrialEligible() -> {
                        createSubscriptionOptions(
                            monthlyOffer = subscriptionOffers.getValue(MONTHLY_FREE_TRIAL_OFFER_ROW),
                            yearlyOffer = subscriptionOffers.getValue(YEARLY_FREE_TRIAL_OFFER_ROW),
                        )
                    }

                    subscriptionOffers.keys.containsAll(listOf(MONTHLY_PLAN_US, YEARLY_PLAN_US)) -> {
                        createSubscriptionOptions(
                            monthlyOffer = subscriptionOffers.getValue(MONTHLY_PLAN_US),
                            yearlyOffer = subscriptionOffers.getValue(YEARLY_PLAN_US),
                        )
                    }

                    subscriptionOffers.keys.containsAll(listOf(MONTHLY_PLAN_ROW, YEARLY_PLAN_ROW)) -> {
                        createSubscriptionOptions(
                            monthlyOffer = subscriptionOffers.getValue(MONTHLY_PLAN_ROW),
                            yearlyOffer = subscriptionOffers.getValue(YEARLY_PLAN_ROW),
                        )
                    }

                    else -> defaultOptions
                }
            } else {
                defaultOptions
            }

            sendOptionJson(subscriptionOptions)
        }
    }

    private suspend fun isFreeTrialEligible(): Boolean {
        return subscriptionsManager.isFreeTrialsEnabled()
    }

    private suspend fun createSubscriptionOptions(
        monthlyOffer: SubscriptionOffer,
        yearlyOffer: SubscriptionOffer,
    ): SubscriptionOptionsJson {
        return SubscriptionOptionsJson(
            options = listOf(
                createOptionsJson(yearlyOffer, YEARLY.lowercase()),
                createOptionsJson(monthlyOffer, MONTHLY.lowercase()),
            ),
            features = monthlyOffer.features.map(::FeatureJson),
        )
    }

    private suspend fun createOptionsJson(offer: SubscriptionOffer, recurrence: String): OptionsJson {
        val offerDisplayPrice: String = offer.offerId?.let {
            offer.pricingPhases.getOrNull(1)?.formattedPrice ?: offer.pricingPhases.first().formattedPrice
        } ?: offer.pricingPhases.first().formattedPrice

        return OptionsJson(
            id = offer.planId,
            cost = CostJson(displayPrice = offerDisplayPrice, recurrence = recurrence),
            offer = getOfferJson(offer),
        )
    }

    private suspend fun getOfferJson(offer: SubscriptionOffer): OfferJson? {
        return offer.offerId?.let {
            val offerType = when {
                LIST_OF_FREE_TRIAL_OFFERS.contains(offer.offerId) -> OfferType.FREE_TRIAL
                else -> OfferType.UNKNOWN
            }

            OfferJson(
                type = offerType.type,
                id = it,
                durationInDays = offer.pricingPhases.first().getBillingPeriodInDays(),
                isUserEligible = !subscriptionsManager.hadTrial(),
            )
        }
    }

    private fun recoverSubscription() {
        viewModelScope.launch {
            command.send(RestoreSubscription)
        }
    }

    private fun backToSettingsActiveSuccess() {
        viewModelScope.launch {
            subscriptionsManager.fetchAndStoreAllData()
            command.send(BackToSettingsActivateSuccess)
        }
    }

    private fun backToSettings() {
        viewModelScope.launch {
            subscriptionsManager.fetchAndStoreAllData()
            command.send(BackToSettings)
        }
    }

    fun paywallShown() {
        pixelSender.reportOfferScreenShown()
    }

    data class SubscriptionOptionsJson(
        val platform: String = PLATFORM,
        val options: List<OptionsJson>,
        val features: List<FeatureJson>,
    )

    data class OptionsJson(
        val id: String,
        val cost: CostJson,
        val offer: OfferJson?,
    )

    data class CostJson(
        val displayPrice: String,
        val recurrence: String,
    )

    data class OfferJson(
        val type: String,
        val id: String,
        val durationInDays: Int?,
        val isUserEligible: Boolean,
    )

    data class FeatureJson(val name: String)

    enum class OfferType(val type: String) {
        FREE_TRIAL("freeTrial"),
        UNKNOWN("unknown"),
    }

    sealed class PurchaseStateView {
        data object Inactive : PurchaseStateView()
        data object InProgress : PurchaseStateView()
        data class Success(val subscriptionEventData: SubscriptionEventData) : PurchaseStateView()
        data object Waiting : PurchaseStateView()
        data object Recovered : PurchaseStateView()
        data object Failure : PurchaseStateView()
    }

    sealed class Command {
        data object BackToSettings : Command()
        data object BackToSettingsActivateSuccess : Command()
        data class SendJsEvent(val event: SubscriptionEventData) : Command()
        data class SendResponseToJs(val data: JsCallbackData) : Command()
        data class SubscriptionSelected(
            val id: String,
            val offerId: String?,
            val experimentName: String?,
            val experimentCohort: String?,
        ) : Command()
        data object RestoreSubscription : Command()
        data object GoToITR : Command()
        data object GoToPIR : Command()
        data class GoToNetP(val activityParams: ActivityParams) : Command()
        data object Reload : Command()
    }

    companion object {
        const val PURCHASE_COMPLETED_FEATURE_NAME = "useSubscription"
        const val PURCHASE_COMPLETED_SUBSCRIPTION_NAME = "onPurchaseUpdate"
        const val PURCHASE_COMPLETED_JSON = """{ type: "completed" }"""
        const val PURCHASE_CANCELED_JSON = """{ type: "canceled" }"""
    }
}
