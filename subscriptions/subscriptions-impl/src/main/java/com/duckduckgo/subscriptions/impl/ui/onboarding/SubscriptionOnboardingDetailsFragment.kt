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

package com.duckduckgo.subscriptions.impl.ui.onboarding

import android.os.Bundle
import android.view.View
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.FragmentSubscriptionOnboardingDetailsBinding

/**
 * The concrete first screen of the onboarding flow (subscription details). Tapping continue starts
 * the plugin-contributed step sequence via the host.
 */
@InjectWith(FragmentScope::class)
class SubscriptionOnboardingDetailsFragment : DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_details) {

    private val binding: FragmentSubscriptionOnboardingDetailsBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.continueButton.setOnClickListener {
            (requireActivity() as SubscriptionOnboardingStepHost).onStepCompleted()
        }
    }
}
