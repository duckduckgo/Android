/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.feedback

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.FAQS_URL
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.OTHER
import com.duckduckgo.subscriptions.impl.internal.SubscriptionsUrlProvider
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface FeedbackHelpUrlProvider {
    fun getUrl(subCategory: SubscriptionFeedbackSubCategory): String
}

@ContributesBinding(ActivityScope::class)
class RealFeedbackHelpUrlProvider @Inject constructor(
    private val subscriptionsUrlProvider: SubscriptionsUrlProvider,
) : FeedbackHelpUrlProvider {
    override fun getUrl(subCategory: SubscriptionFeedbackSubCategory): String {
        return when (subCategory) {
            is SubscriptionFeedbackVpnSubCategory -> getVPNUrl(subCategory)
            is SubscriptionFeedbackSubsSubCategory -> getSubsUrl()
            is SubscriptionFeedbackPirSubCategory -> getPirUrl(subCategory)
            is SubscriptionFeedbackItrSubCategory -> getItrUrl(subCategory)
            is SubscriptionFeedbackDuckAiSubCategory -> getDuckAiSubCategoryUrl(subCategory)
            else -> FAQS_URL
        }
    }

    private fun getItrUrl(subCategory: SubscriptionFeedbackItrSubCategory): String {
        return when (subCategory) {
            SubscriptionFeedbackItrSubCategory.CANT_CONTACT_ADVISOR -> HELP_PAGE_PPRO_ITR_IRIS
            else -> HELP_PAGE_PPRO_ITR
        }
    }

    private fun getPirUrl(subCategory: SubscriptionFeedbackPirSubCategory): String {
        return when (subCategory) {
            SubscriptionFeedbackPirSubCategory.OTHER -> HELP_PAGE_PPRO_PIR
            else -> HELP_PAGE_PPRO_PIR_REMOVAL
        }
    }

    private fun getSubsUrl(): String = HELP_PAGE_PPRO_PAYMENT

    private fun getVPNUrl(subcategory: SubscriptionFeedbackVpnSubCategory): String {
        return when (subcategory) {
            OTHER -> HELP_PAGE_PPRO_VPN
            else -> HELP_PAGE_PPRO_VPN_TROUBLESHOOTING
        }
    }

    private fun getDuckAiSubCategoryUrl(subCategory: SubscriptionFeedbackDuckAiSubCategory): String {
        return when (subCategory) {
            SubscriptionFeedbackDuckAiSubCategory.ACCESS_SUBSCRIPTION_MODELS -> HELP_PAGE_DUCK_AI_ACCESS_SUBSCRIPTION_MODELS
            SubscriptionFeedbackDuckAiSubCategory.LOGIN_THIRD_PARTY_BROWSER -> HELP_PAGE_DUCK_AI_LOGIN_THIRD_PARTY_BROWSER
            SubscriptionFeedbackDuckAiSubCategory.OTHER -> HELP_PAGE_DUCK_AI_OTHER
        }
    }

    companion object {
        private const val HELP_PAGE_PPRO_PAYMENT = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/payments/"
        private const val HELP_PAGE_PPRO_VPN_TROUBLESHOOTING = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/vpn/troubleshooting/"
        private const val HELP_PAGE_PPRO_VPN = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/vpn/"
        private const val HELP_PAGE_PPRO_PIR_REMOVAL =
            "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/personal-information-removal/removal-process/"
        private const val HELP_PAGE_PPRO_PIR = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/personal-information-removal/"
        private const val HELP_PAGE_PPRO_ITR = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/identity-theft-restoration/"
        private const val HELP_PAGE_PPRO_ITR_IRIS = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/identity-theft-restoration/iris/"
        private const val HELP_PAGE_DUCK_AI_ACCESS_SUBSCRIPTION_MODELS =
            "https://duckduckgo.com/duckduckgo-help-pages/duckai/access-subscriber-AI-models"
        private const val HELP_PAGE_DUCK_AI_LOGIN_THIRD_PARTY_BROWSER = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/activating"
        private const val HELP_PAGE_DUCK_AI_OTHER = "https://duckduckgo.com/duckduckgo-help-pages/duckai"
    }
}
