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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.DrawableRes
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionOnboardingFeatureInfoBinding

/** Which onboarding feature a [SubscriptionOnboardingFeatureInfoActivity] describes. */
enum class OnboardingFeature(@DrawableRes val iconRes: Int) {
    VPN(R.drawable.vpn_color_24),
    ITR(R.drawable.identity_theft_restoration_color_24),
    DUCK_AI(R.drawable.ai_general_color_24),
    PIR(R.drawable.identity_blocked_pir_color_24),
}

/**
 * WIP full-screen detail for a single subscription feature, opened from the features-summary step. Toolbar
 * shows a close (X) and no title; closing returns to the features step. Per-feature content is added later.
 */
@InjectWith(ActivityScope::class)
class SubscriptionOnboardingFeatureInfoActivity : DuckDuckGoActivity() {

    private val binding: ActivitySubscriptionOnboardingFeatureInfoBinding by viewBinding()

    private val feature: OnboardingFeature by lazy {
        OnboardingFeature.valueOf(intent.getStringExtra(EXTRA_FEATURE) ?: OnboardingFeature.VPN.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        binding.includeToolbar.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
        supportActionBar?.title = ""
        binding.subscriptionOnboardingFeatureInfoIcon.setImageResource(feature.iconRes)
    }

    companion object {
        private const val EXTRA_FEATURE = "extra_feature"

        fun intent(
            context: Context,
            feature: OnboardingFeature,
        ): Intent = Intent(context, SubscriptionOnboardingFeatureInfoActivity::class.java).apply {
            putExtra(EXTRA_FEATURE, feature.name)
        }
    }
}
