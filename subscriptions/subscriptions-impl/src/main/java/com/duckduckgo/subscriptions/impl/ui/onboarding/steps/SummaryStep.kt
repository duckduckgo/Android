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

package com.duckduckgo.subscriptions.impl.ui.onboarding.steps

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingProgress
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepNavigator
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ViewSummaryOnboardingStepBinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Placeholder SUMMARY screen (always last). Shows total cross-session completion via
 * [SubscriptionOnboardingProgress]; the primary button closes onboarding. Safe to delete with the
 * other placeholders.
 */
@ContributesActivePlugin(
    AppScope::class,
    boundType = SubscriptionOnboardingStepPlugin::class,
    priority = 100,
    featureName = "pluginSummarySubscriptionOnboardingStep",
    parentFeatureName = "pluginPointSubscriptionOnboardingStep",
)
class SummaryStepPlugin @Inject constructor() : SubscriptionOnboardingStepPlugin {

    override val name = "summaryStep"

    override val toolbarTitle = R.string.subscriptionOnboardingSummaryToolbarTitle

    override val stepType = SubscriptionOnboardingStepType.SUMMARY

    override fun getOnboardingStepView(
        context: Context,
        navigator: SubscriptionOnboardingStepNavigator,
    ): View = SummaryStepView(context).apply { this.navigator = navigator }
}

@InjectWith(ViewScope::class)
class SummaryStepView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var onboardingProgress: SubscriptionOnboardingProgress

    var navigator: SubscriptionOnboardingStepNavigator? = null

    private val binding: ViewSummaryOnboardingStepBinding by viewBinding()

    init {
        orientation = VERTICAL
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.primaryButton.setOnClickListener { navigator?.onNextStep() }

        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            val percent = onboardingProgress.completionPercent()
            binding.completionPercent.text = context.getString(R.string.subscriptionOnboardingSummaryPercent, percent)
        }
    }
}
