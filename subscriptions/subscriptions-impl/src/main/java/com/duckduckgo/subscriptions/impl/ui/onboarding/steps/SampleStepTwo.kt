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
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepNavigator
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType
import com.duckduckgo.subscriptions.impl.R
import javax.inject.Inject

/**
 * A second sample STEP (higher priority) so multi-step ordering, completion % and back navigation
 * can be verified. Reuses [SampleStepView] since it's an identical placeholder. Safe to delete with
 * the other placeholders.
 */
@ContributesActivePlugin(
    AppScope::class,
    boundType = SubscriptionOnboardingStepPlugin::class,
    priority = 20,
    featureName = "pluginSampleSubscriptionOnboardingStepTwo",
    parentFeatureName = "pluginPointSubscriptionOnboardingStep",
)
class SampleStepTwoPlugin @Inject constructor() : SubscriptionOnboardingStepPlugin {

    override val name = "sampleStepTwo"

    override val toolbarTitle = R.string.subscriptionOnboardingSampleTwoToolbarTitle

    override val stepType = SubscriptionOnboardingStepType.STEP

    override fun getOnboardingStepView(
        context: Context,
        navigator: SubscriptionOnboardingStepNavigator,
    ): View = SampleStepView(context).apply { this.navigator = navigator }
}
