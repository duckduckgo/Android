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

package com.duckduckgo.subscriptions.impl.onboarding.features

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.api.model.Entitlement
import com.duckduckgo.subscriptions.impl.onboarding.features.SubscriptionOnboardingFeaturesSummaryStepPlugin.Companion.FEATURES_SUMMARY_STEP_ID
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionOnboardingFeaturesSummaryViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val controller: SubscriptionOnboardingController = mock()
    private val subscriptions: Subscriptions = mock()

    private fun viewModelWith(vararg products: Product): SubscriptionOnboardingFeaturesSummaryViewModel {
        val entitlements = products.map { Entitlement(name = it.name, product = it.value) }.toSet()
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(entitlements))
        return SubscriptionOnboardingFeaturesSummaryViewModel(controller, subscriptions)
    }

    @Test
    fun whenAllProductsEntitledThenAllRowsVisible() {
        val state = viewModelWith(Product.NetP, Product.ITR, Product.DuckAiPlus, Product.PIR).viewState.value

        assertTrue(state.vpnVisible)
        assertTrue(state.itrVisible)
        assertTrue(state.aiVisible)
        assertTrue(state.pirVisible)
    }

    @Test
    fun whenOnlyNetPEntitledThenOnlyVpnVisible() {
        val state = viewModelWith(Product.NetP).viewState.value

        assertTrue(state.vpnVisible)
        assertFalse(state.itrVisible)
        assertFalse(state.aiVisible)
        assertFalse(state.pirVisible)
    }

    @Test
    fun whenRowItrEntitledThenItrRowVisible() {
        val state = viewModelWith(Product.ROW_ITR).viewState.value

        assertTrue(state.itrVisible)
    }

    @Test
    fun whenNoEntitlementsThenAllRowsHidden() {
        val state = viewModelWith().viewState.value

        assertFalse(state.vpnVisible)
        assertFalse(state.itrVisible)
        assertFalse(state.aiVisible)
        assertFalse(state.pirVisible)
    }

    @Test
    fun whenPrimaryCtaClickedThenCompletesFeaturesSummaryStep() {
        viewModelWith(Product.NetP).onPrimaryCtaClicked()

        verify(controller).onStepFinished(FEATURES_SUMMARY_STEP_ID, COMPLETED)
    }
}
