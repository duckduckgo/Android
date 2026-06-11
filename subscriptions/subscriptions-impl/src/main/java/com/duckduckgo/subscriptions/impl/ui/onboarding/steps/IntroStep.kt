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
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepNavigator
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ViewIntroOnboardingStepBinding
import javax.inject.Inject

/**
 * Placeholder INTRO screen (confetti + subscription details). Shown only when onboarding is entered
 * after a purchase; skipped when re-entering from settings. Confetti is rendered by the host
 * fragment for INTRO screens. Safe to delete with the other placeholders.
 */
@ContributesActivePlugin(
    AppScope::class,
    boundType = SubscriptionOnboardingStepPlugin::class,
    priority = 0,
    featureName = "pluginIntroSubscriptionOnboardingStep",
    parentFeatureName = "pluginPointSubscriptionOnboardingStep",
)
class IntroStepPlugin @Inject constructor() : SubscriptionOnboardingStepPlugin {

    override val name = "introStep"

    override val toolbarTitle = R.string.subscriptionOnboardingIntroToolbarTitle

    override val stepType = SubscriptionOnboardingStepType.INTRO

    override fun getOnboardingStepView(
        context: Context,
        navigator: SubscriptionOnboardingStepNavigator,
    ): View = IntroStepView(context).apply { this.navigator = navigator }
}

class IntroStepView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    var navigator: SubscriptionOnboardingStepNavigator? = null

    private val binding: ViewIntroOnboardingStepBinding by viewBinding()

    init {
        orientation = VERTICAL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.primaryButton.setOnClickListener { navigator?.onNextStep() }
    }
}
