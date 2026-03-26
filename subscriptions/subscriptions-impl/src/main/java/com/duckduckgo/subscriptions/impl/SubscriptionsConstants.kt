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

import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_OF_PLUS_PLANS
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_OF_PRO_PLANS

object SubscriptionsConstants {

    // List of subscriptions
    const val BASIC_SUBSCRIPTION = "ddg_privacy_pro"
    const val ADVANCED_SUBSCRIPTION = "ddg_subscription_pro"
    val LIST_OF_PRODUCTS = listOf(BASIC_SUBSCRIPTION, ADVANCED_SUBSCRIPTION)

    // List of plans
    const val YEARLY_PLAN_US = "ddg-privacy-pro-yearly-renews-us"
    const val MONTHLY_PLAN_US = "ddg-privacy-pro-monthly-renews-us"
    const val YEARLY_PLAN_ROW = "ddg-privacy-pro-yearly-renews-row"
    const val MONTHLY_PLAN_ROW = "ddg-privacy-pro-monthly-renews-row"

    val LIST_MONTHLY_PLUS_PLANS = listOf(MONTHLY_PLAN_US, MONTHLY_PLAN_ROW)
    val LIST_YEARLY_PLUS_PLANS = listOf(YEARLY_PLAN_US, YEARLY_PLAN_ROW)

    val LIST_OF_PLUS_PLANS = LIST_MONTHLY_PLUS_PLANS + LIST_YEARLY_PLUS_PLANS

    const val YEARLY_PRO_PLAN_US = "ddg-subscription-pro-yearly-renews-us"
    const val MONTHLY_PRO_PLAN_US = "ddg-subscription-pro-monthly-renews-us"
    const val YEARLY_PRO_PLAN_ROW = "ddg-subscription-pro-yearly-renews-row"
    const val MONTHLY_PRO_PLAN_ROW = "ddg-subscription-pro-monthly-renews-row"

    val LIST_MONTHLY_PRO_PLANS = listOf(MONTHLY_PRO_PLAN_US, MONTHLY_PRO_PLAN_ROW)
    val LIST_YEARLY_PRO_PLANS = listOf(YEARLY_PRO_PLAN_US, YEARLY_PRO_PLAN_ROW)

    val LIST_OF_PRO_PLANS = LIST_MONTHLY_PRO_PLANS + LIST_YEARLY_PRO_PLANS

    // List of offers (Plus free trial)
    const val MONTHLY_FREE_TRIAL_OFFER_US = "ddg-privacy-pro-freetrial-monthly-renews-us"
    const val YEARLY_FREE_TRIAL_OFFER_US = "ddg-privacy-pro-freetrial-yearly-renews-us"
    const val MONTHLY_FREE_TRIAL_OFFER_ROW = "ddg-privacy-pro-freetrial-monthly-renews-row"
    const val YEARLY_FREE_TRIAL_OFFER_ROW = "ddg-privacy-pro-freetrial-yearly-renews-row"
    val LIST_OF_PLUS_FREE_TRIAL_OFFERS =
        listOf(MONTHLY_FREE_TRIAL_OFFER_US, YEARLY_FREE_TRIAL_OFFER_US, MONTHLY_FREE_TRIAL_OFFER_ROW, YEARLY_FREE_TRIAL_OFFER_ROW)

    // List of offers (Pro free trial)
    const val MONTHLY_PRO_FREE_TRIAL_OFFER_US = "ddg-subscription-pro-freetrial-monthly-renews-us"
    const val YEARLY_PRO_FREE_TRIAL_OFFER_US = "ddg-subscription-pro-freetrial-yearly-renews-us"
    const val MONTHLY_PRO_FREE_TRIAL_OFFER_ROW = "ddg-subscription-pro-freetrial-monthly-renews-row"
    const val YEARLY_PRO_FREE_TRIAL_OFFER_ROW = "ddg-subscription-pro-freetrial-yearly-renews-row"
    val LIST_OF_PRO_FREE_TRIAL_OFFERS =
        listOf(MONTHLY_PRO_FREE_TRIAL_OFFER_US, YEARLY_PRO_FREE_TRIAL_OFFER_US, MONTHLY_PRO_FREE_TRIAL_OFFER_ROW, YEARLY_PRO_FREE_TRIAL_OFFER_ROW)

    // Combined list of all free trial offers
    val LIST_OF_FREE_TRIAL_OFFERS = LIST_OF_PLUS_FREE_TRIAL_OFFERS + LIST_OF_PRO_FREE_TRIAL_OFFERS

    // List of features
    const val LEGACY_FE_NETP = "vpn"
    const val LEGACY_FE_ITR = "identity-theft-restoration"
    const val LEGACY_FE_PIR = "personal-information-removal"

    const val NETP = "Network Protection"
    const val ITR = "Identity Theft Restoration"
    const val ROW_ITR = "Global Identity Theft Restoration"
    const val PIR = "Data Broker Protection"
    const val DUCK_AI = "Duck.ai"

    // Platform
    const val PLATFORM = "android"

    // Recurrence
    const val MONTHLY = "Monthly"
    const val YEARLY = "Yearly"

    // URLs
    const val ITR_URL = "https://duckduckgo.com/identity-theft-restoration"
    const val FAQS_URL = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/"
    const val PRIVACY_PRO_ETLD = "duckduckgo.com"
    const val FEATURE_PAGE_QUERY_PARAM_KEY = "featurePage"
    const val PRIVACY_PRO_PATH = "pro"
    const val PRIVACY_SUBSCRIPTIONS_PATH = "subscriptions"
}

enum class SubscriptionTier(val value: String) {
    PLUS("plus"),
    PRO("pro"),
    UNKNOWN("unknown"),
    ;

    val productId: String
        get() = when (this) {
            PLUS -> SubscriptionsConstants.BASIC_SUBSCRIPTION
            PRO -> SubscriptionsConstants.ADVANCED_SUBSCRIPTION
            UNKNOWN -> SubscriptionsConstants.BASIC_SUBSCRIPTION // fallback to basic
        }

    companion object {
        private val PLAN_TO_TIER: Map<SubscriptionTier, List<String>> = mapOf(
            PLUS to LIST_OF_PLUS_PLANS,
            PRO to LIST_OF_PRO_PLANS,
        )

        fun fromPlanId(planId: String): SubscriptionTier {
            return PLAN_TO_TIER.entries.find { it.value.contains(planId) }?.key ?: UNKNOWN
        }

        fun fromTierString(tierString: String): SubscriptionTier {
            return entries.find { it.value == tierString } ?: UNKNOWN
        }
    }
}
