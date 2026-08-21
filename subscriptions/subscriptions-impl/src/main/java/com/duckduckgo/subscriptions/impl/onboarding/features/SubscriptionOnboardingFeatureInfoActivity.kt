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
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingFeature
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionOnboardingFeatureInfoScreen
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionOnboardingFeatureInfoBinding
import javax.inject.Inject

/**
 * Full-screen detail for a single subscription feature, opened from the features-summary step. Toolbar
 * shows a close (X) and no title; closing returns to the features step. Per-feature copy and highlights
 * come from [OnboardingFeature], whose content layout is inflated into the screen.
 */
@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionOnboardingFeatureInfoScreen::class)
class SubscriptionOnboardingFeatureInfoActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivitySubscriptionOnboardingFeatureInfoBinding by viewBinding()

    private val feature: OnboardingFeature by lazy {
        val screenFeature = intent.getActivityParams(SubscriptionOnboardingFeatureInfoScreen::class.java)?.feature
            ?: SubscriptionOnboardingFeature.VPN
        when (screenFeature) {
            SubscriptionOnboardingFeature.VPN -> OnboardingFeature.VPN
            SubscriptionOnboardingFeature.ITR -> OnboardingFeature.ITR
            SubscriptionOnboardingFeature.DUCK_AI -> OnboardingFeature.DUCK_AI
            SubscriptionOnboardingFeature.PIR -> OnboardingFeature.PIR
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.MISC)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        binding.includeToolbar.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
        supportActionBar?.title = ""

        val surfaceColor = getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface)
        binding.includeToolbar.appBarLayout.setBackgroundColor(surfaceColor)
        binding.includeToolbar.toolbar.setBackgroundColor(surfaceColor)
        binding.subscriptionOnboardingFeatureInfoIcon.setImageResource(feature.iconRes)
        binding.subscriptionOnboardingFeatureInfoTitle.setText(feature.titleRes)
        binding.subscriptionOnboardingFeatureInfoDescription.setText(feature.descriptionRes)
        layoutInflater.inflate(feature.contentRes, binding.subscriptionOnboardingFeatureInfoContent, true)

        if (edgeToEdgeEnabled) {
            edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
            edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout, installScrim = false)
            edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.subscriptionOnboardingFeatureInfoScrollView)
        }
    }
}

enum class OnboardingFeature(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @LayoutRes val contentRes: Int,
) {
    VPN(
        iconRes = R.drawable.vpn_feature_128,
        titleRes = R.string.subscriptionOnboardingFeatureInfoVpnTitle,
        descriptionRes = R.string.subscriptionOnboardingFeatureInfoVpnDescription,
        contentRes = R.layout.content_subscription_onboarding_feature_info_vpn,
    ),
    ITR(
        iconRes = R.drawable.identity_theft_restoration_feature_128,
        titleRes = R.string.subscriptionOnboardingFeatureInfoItrTitle,
        descriptionRes = R.string.subscriptionOnboardingFeatureInfoItrDescription,
        contentRes = R.layout.content_subscription_onboarding_feature_info_idtr,
    ),
    DUCK_AI(
        iconRes = R.drawable.duckai_ddg_feature_128,
        titleRes = R.string.subscriptionOnboardingFeatureInfoDuckAiTitle,
        descriptionRes = R.string.subscriptionOnboardingFeatureInfoDuckAiDescription,
        contentRes = R.layout.content_subscription_onboarding_feature_info_duckai,
    ),
    PIR(
        iconRes = R.drawable.personal_information_remover_feature_128,
        titleRes = R.string.subscriptionOnboardingFeatureInfoPirTitle,
        descriptionRes = R.string.subscriptionOnboardingFeatureInfoPirDescription,
        contentRes = R.layout.content_subscription_onboarding_feature_info_pir,
    ),
}
