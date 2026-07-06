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

package com.duckduckgo.subscriptions.impl.onboarding

import android.content.Intent
import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.SettingsScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionOnboardingScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionOnboardingBinding
import javax.inject.Inject

/**
 * Entry point for the native subscription onboarding flow. Step 1 is a placeholder welcome screen; the primary
 * button finishes to Settings.
 *
 * The multi-step flow driven by the LinearOnboardingOrchestrator (plan/steps/events, fragment-swapping) lands
 * in a follow-up step.
 */
@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionOnboardingScreenWithEmptyParams::class, screenName = "subscriptions.onboarding")
class SubscriptionOnboardingActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ActivitySubscriptionOnboardingBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.subscriptionOnboardingWelcomeContinueButton.setOnClickListener {
            finishToSettings()
        }
    }

    private fun finishToSettings() {
        globalActivityStarter.startIntent(this, SettingsScreenNoParams)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        finish()
    }
}
