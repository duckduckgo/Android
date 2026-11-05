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

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.GoBack
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingEvent.BackPressed
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingEvent.StepFinished
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStore
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

@SingleIn(AppScope::class)
class SubscriptionOnboardingPlanProvider @Inject constructor(
    private val stepPlugins: PluginPoint<SubscriptionOnboardingStepPlugin>,
    private val onboardingStore: SubscriptionOnboardingStore,
) {

    suspend fun buildPlan(): LinearOnboardingPlan {
        val (available, unavailable) = stepPlugins.getPlugins().partition { it.shouldShow() }
        unavailable.forEach { onboardingStore.setStepCompleted(it.stepId) }
        return LinearOnboardingPlan(
            id = SUBSCRIPTION_ONBOARDING_PLAN_ID,
            steps = available.map { stepPlugin -> activityStep(stepPlugin) },
        )
    }

    private fun activityStep(stepPlugin: SubscriptionOnboardingStepPlugin): SubscriptionOnboardingActivityStep =
        SubscriptionOnboardingActivityStep(
            id = stepPlugin.stepId,
            stepPlugin = stepPlugin,
            precondition = { !onboardingStore.isStepCompleted(stepPlugin.stepId) },
            transition = { event ->
                when {
                    event is StepFinished && event.stepId == stepPlugin.stepId -> Advance
                    event is BackPressed -> GoBack
                    else -> Stay
                }
            },
        )

    companion object {
        const val SUBSCRIPTION_ONBOARDING_PLAN_ID = "subscription_onboarding"
    }
}
