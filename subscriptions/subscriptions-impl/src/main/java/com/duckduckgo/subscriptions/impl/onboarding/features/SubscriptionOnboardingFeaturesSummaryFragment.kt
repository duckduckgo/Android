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

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.FragmentSubscriptionOnboardingFeaturesSummaryBinding
import com.duckduckgo.subscriptions.impl.onboarding.features.SubscriptionOnboardingFeaturesSummaryViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SubscriptionOnboardingFeaturesSummaryFragment :
    DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_features_summary) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentSubscriptionOnboardingFeaturesSummaryBinding by viewBinding()
    private val viewModel: SubscriptionOnboardingFeaturesSummaryViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SubscriptionOnboardingFeaturesSummaryViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            subscriptionOnboardingFeaturesVPN.setClickListener { openFeatureInfo(OnboardingFeature.VPN) }
            subscriptionOnboardingFeaturesITR.setClickListener { openFeatureInfo(OnboardingFeature.ITR) }
            subscriptionOnboardingFeaturesAi.setClickListener { openFeatureInfo(OnboardingFeature.DUCK_AI) }
            subscriptionOnboardingFeaturesPIR.setClickListener { openFeatureInfo(OnboardingFeature.PIR) }
            subscriptionOnboardingFeaturesPrimaryButton.setOnClickListener { viewModel.onPrimaryCtaClicked() }
        }
        viewModel.viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(viewState: ViewState) = with(binding) {
        subscriptionOnboardingFeaturesVPN.isVisible = viewState.vpnVisible
        subscriptionOnboardingFeaturesITR.isVisible = viewState.itrVisible
        subscriptionOnboardingFeaturesAi.isVisible = viewState.aiVisible
        subscriptionOnboardingFeaturesPIR.isVisible = viewState.pirVisible

        // A divider shows only when a visible row precedes another visible row below it.
        onboardingSubscriptionFeaturesDivider1.isVisible =
            viewState.vpnVisible && (viewState.itrVisible || viewState.aiVisible || viewState.pirVisible)
        onboardingSubscriptionFeaturesDivider2.isVisible =
            viewState.itrVisible && (viewState.aiVisible || viewState.pirVisible)
        onboardingSubscriptionFeaturesDivider3.isVisible = viewState.aiVisible && viewState.pirVisible
    }

    private fun openFeatureInfo(feature: OnboardingFeature) {
        startActivity(SubscriptionOnboardingFeatureInfoActivity.intent(requireContext(), feature))
    }
}
