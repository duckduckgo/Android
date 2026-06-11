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

package com.duckduckgo.subscriptions.impl.ui.onboarding.sample

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepNavigator
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.impl.R
import javax.inject.Inject

/**
 * Sample onboarding step used to exercise the flow end-to-end on internal builds. Safe to delete
 * once real feature steps (VPN, Duck.ai, …) are contributed. Add a second sample with a higher
 * `priority` to verify multi-step ordering and the final close.
 */
@ContributesActivePlugin(
    AppScope::class,
    boundType = SubscriptionOnboardingStepPlugin::class,
    priority = 10,
    featureName = "pluginSampleSubscriptionOnboardingStep",
    parentFeatureName = "pluginPointSubscriptionOnboardingStep",
)
class SampleOnboardingStepPlugin @Inject constructor() : SubscriptionOnboardingStepPlugin {

    override val name = "sampleStep"

    override val toolbarTitle = R.string.subscriptionOnboardingSampleToolbarTitle

    override fun getOnboardingStepView(
        context: Context,
        navigator: SubscriptionOnboardingStepNavigator,
    ): View = SampleOnboardingStepView(context).apply { this.navigator = navigator }
}
