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

package com.duckduckgo.duckchat.impl.subscriptions.onboarding

import android.os.Bundle
import android.view.View
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentSubscriptionOnboardingDuckAiBinding
import com.duckduckgo.duckchat.impl.subscriptions.onboarding.SubscriptionOnboardingDuckAiStepPlugin.Companion.DUCK_AI_STEP_ID
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepHost
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.requireSubscriptionOnboardingStepHost

/**
 * Duck.ai step of the native subscription onboarding: a placeholder screen with a primary button that
 * completes the step. As the last step, completing it finishes the onboarding. Reports back through
 * [SubscriptionOnboardingStepHost] so it stays decoupled from the host activity and the onboarding framework.
 */
@InjectWith(FragmentScope::class)
class SubscriptionOnboardingDuckAiFragment : DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_duck_ai) {

    private val binding: FragmentSubscriptionOnboardingDuckAiBinding by viewBinding()

    private val stepHost: SubscriptionOnboardingStepHost
        get() = requireSubscriptionOnboardingStepHost()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.subscriptionOnboardingDuckAiNextButton.setOnClickListener {
            stepHost.onStepFinished(DUCK_AI_STEP_ID, COMPLETED)
        }
    }
}
