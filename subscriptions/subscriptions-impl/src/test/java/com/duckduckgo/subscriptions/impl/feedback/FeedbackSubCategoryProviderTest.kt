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

import android.annotation.SuppressLint
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@SuppressLint("DenyListedApi")
class FeedbackSubCategoryProviderTest {

    private val privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)
    private lateinit var provider: RealFeedbackSubCategoryProvider

    @Before
    fun setUp() {
        provider = RealFeedbackSubCategoryProvider(privacyProFeature)
    }

    @Test
    fun whenAllowProTierPurchaseEnabledThenUnableToAccessFeaturesIsIncluded() {
        privacyProFeature.allowProTierPurchase().setRawStoredState(Toggle.State(enable = true))

        val subCategories = provider.getSubCategories(SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS)

        assertTrue(subCategories.containsValue(SubscriptionFeedbackSubsSubCategory.UNABLE_TO_ACCESS_FEATURES))
        assertEquals(3, subCategories.size)
    }

    @Test
    fun whenAllowProTierPurchaseDisabledThenUnableToAccessFeaturesIsNotIncluded() {
        privacyProFeature.allowProTierPurchase().setRawStoredState(Toggle.State(enable = false))

        val subCategories = provider.getSubCategories(SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS)

        assertFalse(subCategories.containsValue(SubscriptionFeedbackSubsSubCategory.UNABLE_TO_ACCESS_FEATURES))
        assertEquals(2, subCategories.size)
    }

    @Test
    fun whenAllowProTierPurchaseEnabledThenSubsSubCategoriesContainsCorrectItems() {
        privacyProFeature.allowProTierPurchase().setRawStoredState(Toggle.State(enable = true))

        val subCategories = provider.getSubCategories(SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS)

        assertTrue(subCategories.containsKey(R.string.feedbackSubCategorySubsOtp))
        assertTrue(subCategories.containsKey(R.string.feedbackSubCategorySubsUnableToAccessProFeatures))
        assertTrue(subCategories.containsKey(R.string.feedbackSubCategorySubsOther))
    }

    @Test
    fun whenAllowProTierPurchaseDisabledThenSubsSubCategoriesContainsOnlyOtpAndOther() {
        privacyProFeature.allowProTierPurchase().setRawStoredState(Toggle.State(enable = false))

        val subCategories = provider.getSubCategories(SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS)

        assertTrue(subCategories.containsKey(R.string.feedbackSubCategorySubsOtp))
        assertFalse(subCategories.containsKey(R.string.feedbackSubCategorySubsUnableToAccessProFeatures))
        assertTrue(subCategories.containsKey(R.string.feedbackSubCategorySubsOther))
    }
}
