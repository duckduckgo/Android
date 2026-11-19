/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.stripe

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.subscriptions.impl.AccessTokenResult
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.repository.isActive
import com.duckduckgo.subscriptions.impl.stripe.RealStripeTierOptionsProvider.Companion.STRIPE_PLATFORM
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Companion.PURCHASE_COMPLETED_FEATURE_NAME
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Companion.PURCHASE_COMPLETED_SUBSCRIPTION_NAME
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.CostJson
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.OptionsJson
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.ProductJson
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.SubscriptionTierOptionsJson
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.TierFeatureJson
import com.duckduckgo.subscriptions.impl.wideevents.SubscriptionSwitchWideEvent
import dev.zacsweers.metro.ContributesBinding
import org.json.JSONObject
import javax.inject.Inject

/**
 * Handles the plans page for users whose subscription was bought on Stripe.
 */
interface StripePlansHandler {
    /** Whether the plans page should use Stripe instead of Google Play for [subscription]. */
    fun shouldHandle(subscription: Subscription?): Boolean

    /** Returns the Stripe products to show on the plans page. */
    suspend fun getTierOptions(): SubscriptionTierOptionsJson

    /** Returns the event that tells the plans page to send the user to Stripe, or null if there is no access token. */
    suspend fun getRedirectEvent(
        subscription: Subscription,
        planId: String,
        change: String?,
        origin: String?,
    ): SubscriptionEventData?

    /** Refreshes the local subscription data after the user changed plan on Stripe. */
    suspend fun onPlanChangeCompleted()
}

@ContributesBinding(AppScope::class)
class RealStripePlansHandler @Inject constructor(
    private val subscriptionsFeature: SubscriptionsFeature,
    private val subscriptionsManager: SubscriptionsManager,
    private val stripeTierOptionsProvider: StripeTierOptionsProvider,
    private val subscriptionSwitchWideEvent: SubscriptionSwitchWideEvent,
) : StripePlansHandler {

    private var tierProducts: List<StripeTierProduct> = emptyList()

    override fun shouldHandle(subscription: Subscription?): Boolean {
        return subscriptionsFeature.stripeTierOptions().isEnabled() &&
            subscription != null &&
            subscription.isActive() &&
            subscription.platform.equals(STRIPE_PLATFORM, ignoreCase = true)
    }

    override suspend fun getTierOptions(): SubscriptionTierOptionsJson {
        val includeProTier = subscriptionsFeature.allowProTierPurchase().isEnabled()
        val country = if (subscriptionsFeature.stripeTierOptionsByCountry().isEnabled()) {
            subscriptionsManager.getSubscription()?.planCountry
        } else {
            null
        }
        tierProducts = stripeTierOptionsProvider.getTierProducts(includeProTier, country)
        val products = tierProducts.map { product ->
            ProductJson(
                tier = product.tier,
                features = product.features.map { TierFeatureJson(product = it.product, name = it.name) },
                options = product.options.map { option ->
                    OptionsJson(
                        id = option.id,
                        cost = CostJson(displayPrice = option.displayPrice, recurrence = option.recurrence),
                        offer = null,
                    )
                },
            )
        }
        return SubscriptionTierOptionsJson(platform = STRIPE_PLATFORM, products = products)
    }

    override suspend fun getRedirectEvent(
        subscription: Subscription,
        planId: String,
        change: String?,
        origin: String?,
    ): SubscriptionEventData? {
        onSwitchFlowStarted(subscription, planId, change, origin)

        val accessToken = (subscriptionsManager.getAccessToken() as? AccessTokenResult.Success)?.accessToken
        if (accessToken == null) {
            subscriptionSwitchWideEvent.onStripeRedirectFailure()
            return null
        }
        subscriptionSwitchWideEvent.onStripeRedirected()
        return SubscriptionEventData(
            featureName = PURCHASE_COMPLETED_FEATURE_NAME,
            subscriptionName = PURCHASE_COMPLETED_SUBSCRIPTION_NAME,
            params = JSONObject()
                .put("type", PURCHASE_REDIRECT_TYPE)
                .put("token", accessToken),
        )
    }

    override suspend fun onPlanChangeCompleted() {
        subscriptionSwitchWideEvent.onSwitchConfirmationSuccess()
        // Entitlements can change with the new plan, so the tokens are refreshed along with the subscription
        runCatching { subscriptionsManager.refreshAccessToken(force = true) }
        runCatching { subscriptionsManager.refreshSubscriptionData() }
    }

    private suspend fun onSwitchFlowStarted(
        subscription: Subscription,
        planId: String,
        change: String?,
        origin: String?,
    ) {
        // Stripe plan IDs aren't in the Play plan lists, so use the plans page products
        val currentProduct = tierProducts.find { product -> product.options.any { it.id == subscription.productId } }
        val targetProduct = tierProducts.find { product -> product.options.any { it.id == planId } }
        val targetOption = targetProduct?.options?.find { it.id == planId }

        subscriptionSwitchWideEvent.onStripeSwitchFlowStarted(
            context = origin,
            fromPlan = subscription.productId,
            toPlan = planId,
            fromTier = currentProduct?.tier,
            toTier = targetProduct?.tier,
            fromBillingPeriod = subscription.billingPeriod,
            toBillingPeriod = targetOption?.recurrence,
            change = change,
        )
    }

    companion object {
        private const val PURCHASE_REDIRECT_TYPE = "redirect"
    }
}
