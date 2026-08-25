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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.onboarding.features.SubscriptionOnboardingFeaturesSummaryStepPlugin.Companion.FEATURES_SUMMARY_STEP_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class SubscriptionOnboardingFeaturesSummaryViewModel @Inject constructor(
    private val controller: SubscriptionOnboardingController,
    private val subscriptions: Subscriptions,
) : ViewModel() {

    data class ViewState(
        val vpnVisible: Boolean = false,
        val itrVisible: Boolean = false,
        val aiVisible: Boolean = false,
        val pirVisible: Boolean = false,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    init {
        subscriptions.getEntitlements()
            .onEach { entitlements ->
                val products = entitlements.map { it.product }.toSet()
                _viewState.update {
                    it.copy(
                        vpnVisible = Product.NetP.value in products,
                        itrVisible = Product.ITR.value in products || Product.ROW_ITR.value in products,
                        aiVisible = Product.DuckAiPlus.value in products,
                        pirVisible = Product.PIR.value in products,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onPrimaryCtaClicked() {
        controller.onStepFinished(FEATURES_SUMMARY_STEP_ID, COMPLETED)
    }
}
