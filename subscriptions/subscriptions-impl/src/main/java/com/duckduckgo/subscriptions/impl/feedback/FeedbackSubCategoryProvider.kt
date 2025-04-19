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

import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.ITR
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.PIR
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.VPN
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface FeedbackSubCategoryProvider {
    fun getSubCategories(category: SubscriptionFeedbackCategory): Map<Int, SubscriptionFeedbackSubCategory>
}

@ContributesBinding(FragmentScope::class)
class RealFeedbackSubCategoryProvider @Inject constructor() : FeedbackSubCategoryProvider {
    override fun getSubCategories(category: SubscriptionFeedbackCategory): Map<Int, SubscriptionFeedbackSubCategory> {
        return when (category) {
            VPN -> getVPNSubCategories()
            SUBS_AND_PAYMENTS -> getSubsSubCategories()
            PIR -> getPirSubCategories()
            ITR -> getItrSubCategories()
        }
    }

    private fun getVPNSubCategories(): Map<Int, SubscriptionFeedbackSubCategory> {
        return mapOf(
            R.string.feedbackSubCategoryVpnConnection to SubscriptionFeedbackVpnSubCategory.FAILS_TO_CONNECT,
            R.string.feedbackSubCategoryVpnSlow to SubscriptionFeedbackVpnSubCategory.SLOW_CONNECTION,
            R.string.feedbackSubCategoryVpnOtherApps to SubscriptionFeedbackVpnSubCategory.ISSUES_WITH_APPS_OR_WEBSITES,
            R.string.feedbackSubCategoryVpnIot to SubscriptionFeedbackVpnSubCategory.CANNOT_CONNECT_TO_LOCAL_DEVICE,
            R.string.feedbackSubCategoryVpnCrash to SubscriptionFeedbackVpnSubCategory.BROWSER_CRASH_FREEZE,
            R.string.feedbackSubCategoryVpnOther to SubscriptionFeedbackVpnSubCategory.OTHER,
        )
    }

    private fun getSubsSubCategories(): Map<Int, SubscriptionFeedbackSubCategory> {
        return mapOf(
            R.string.feedbackSubCategorySubsOtp to SubscriptionFeedbackSubsSubCategory.ONE_TIME_PASSWORD,
            R.string.feedbackSubCategorySubsOther to SubscriptionFeedbackSubsSubCategory.OTHER,
        )
    }

    private fun getPirSubCategories(): Map<Int, SubscriptionFeedbackSubCategory> {
        return mapOf(
            R.string.feedbackSubCategoryPirNothingOnSpecificSite to SubscriptionFeedbackPirSubCategory.INFO_NOT_ON_SPECIFIC_SITE,
            R.string.feedbackSubCategoryPirNotMe to SubscriptionFeedbackPirSubCategory.RECORDS_NOT_ON_USER,
            R.string.feedbackSubCategoryPirScanStuck to SubscriptionFeedbackPirSubCategory.SCAN_STUCK,
            R.string.feedbackSubCategoryPirRemovalStuck to SubscriptionFeedbackPirSubCategory.REMOVAL_STUCK,
            R.string.feedbackSubCategoryPirOther to SubscriptionFeedbackPirSubCategory.OTHER,
        )
    }

    private fun getItrSubCategories(): Map<Int, SubscriptionFeedbackSubCategory> {
        return mapOf(
            R.string.feedbackSubCategoryItrAccessCode to SubscriptionFeedbackItrSubCategory.ACCESS_CODE_ISSUE,
            R.string.feedbackSubCategoryItrCantContactAdvisor to SubscriptionFeedbackItrSubCategory.CANT_CONTACT_ADVISOR,
            R.string.feedbackSubCategoryItrAdvisorUnhelpful to SubscriptionFeedbackItrSubCategory.UNHELPFUL,
            R.string.feedbackSubCategoryItrOther to SubscriptionFeedbackItrSubCategory.OTHER,
        )
    }
}
