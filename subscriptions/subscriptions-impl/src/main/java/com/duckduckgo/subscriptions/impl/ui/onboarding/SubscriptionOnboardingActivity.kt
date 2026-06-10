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
import androidx.fragment.app.commit
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionOnboardingBinding

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionOnboardingActivity.Companion.SubscriptionOnboardingScreenWithEmptyParams::class)
class SubscriptionOnboardingActivity : DuckDuckGoActivity() {

    private val binding: ActivitySubscriptionOnboardingBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)

        if (supportFragmentManager.findFragmentByTag(DETAILS_FRAGMENT_TAG) == null) {
            supportFragmentManager.commit {
                replace(R.id.onboardingFragmentContainer, SubscriptionOnboardingDetailsFragment(), DETAILS_FRAGMENT_TAG)
            }
        }
    }

    companion object {
        private const val DETAILS_FRAGMENT_TAG = "subscriptionOnboardingDetails"

        data object SubscriptionOnboardingScreenWithEmptyParams : GlobalActivityStarter.ActivityParams {
            private fun readResolve(): Any = SubscriptionOnboardingScreenWithEmptyParams
        }
    }
}
