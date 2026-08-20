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

package com.duckduckgo.subscriptions.impl.onboarding.welcome

import android.os.Bundle
import android.view.View
import androidx.core.view.doOnLayout
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
import com.duckduckgo.subscriptions.impl.databinding.FragmentSubscriptionOnboardingWelcomeBinding
import com.duckduckgo.subscriptions.impl.onboarding.welcome.SubscriptionOnboardingWelcomeViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SubscriptionOnboardingWelcomeFragment : DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_welcome) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentSubscriptionOnboardingWelcomeBinding by viewBinding()
    private val viewModel: SubscriptionOnboardingWelcomeViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SubscriptionOnboardingWelcomeViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.subscriptionOnboardingWelcomePrimaryButton.setOnClickListener {
            viewModel.onPrimaryCtaClicked()
        }
        viewModel.viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        if (savedInstanceState == null) {
            binding.subscriptionOnboardingWelcomeKonfetti.doOnLayout {
                binding.subscriptionOnboardingWelcomeKonfetti.launchOnboardingConfetti()
            }
        }
    }

    private fun render(viewState: ViewState) {
        binding.subscriptionOnboardingWelcomeBannerDescription.text =
            getString(R.string.subscriptionOnboardingWelcomeBannerDescription, viewState.formattedBillingDate)

        val dayViews = with(binding) {
            listOf(
                freeTrialBannerDay1,
                freeTrialBannerDay2,
                freeTrialBannerDay3,
                freeTrialBannerDay4,
                freeTrialBannerDay5,
                freeTrialBannerDay6,
                freeTrialBannerDay7,
            )
        }
        viewState.freeTrialDayLabels.forEachIndexed { index, label -> dayViews[index].text = label }
    }
}
