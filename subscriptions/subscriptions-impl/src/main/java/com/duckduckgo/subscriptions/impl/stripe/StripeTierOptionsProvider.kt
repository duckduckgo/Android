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
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.services.BillingCycleResponse
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.services.TierFeatureResponse
import dev.zacsweers.metro.ContributesBinding
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

interface StripeTierOptionsProvider {
    /**
     * Returns the Stripe products to show on the plans page
     */
    suspend fun getTierProducts(includeProTier: Boolean): List<StripeTierProduct>
}

data class StripeTierProduct(
    val tier: String,
    val features: List<TierFeatureResponse>,
    val options: List<StripeTierOption>,
)

data class StripeTierOption(
    val id: String,
    val displayPrice: String,
    val recurrence: String,
)

@ContributesBinding(AppScope::class)
class RealStripeTierOptionsProvider @Inject constructor(
    private val subscriptionsService: SubscriptionsService,
) : StripeTierOptionsProvider {

    override suspend fun getTierProducts(includeProTier: Boolean): List<StripeTierProduct> {
        val response = runCatching {
            subscriptionsService.tierProducts(region = US_REGION, platform = STRIPE_PLATFORM)
        }.getOrElse { return emptyList() }

        return response.products
            .filter { includeProTier || !it.tier.equals(SubscriptionTier.PRO.value, ignoreCase = true) }
            .mapNotNull { product ->
                val options = product.billingCycles.map { it.toTierOption() }
                if (options.isEmpty()) return@mapNotNull null
                StripeTierProduct(tier = product.tier, features = product.entitlements, options = options)
            }
    }

    private fun BillingCycleResponse.toTierOption(): StripeTierOption {
        return StripeTierOption(
            id = productId,
            displayPrice = formatPrice(price, currency),
            recurrence = period.lowercase(),
        )
    }

    private fun formatPrice(price: String, currency: String): String {
        return runCatching {
            NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                this.currency = Currency.getInstance(currency.uppercase())
            }.format(price.toBigDecimal())
        }.getOrDefault("$price $currency")
    }

    companion object {
        const val STRIPE_PLATFORM = "stripe"
        private const val US_REGION = "us"
    }
}
