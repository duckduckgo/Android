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
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStepStore
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Builds the linear-onboarding plan from the step plugins contributed across feature modules. Plugins are
 * collected in `@PriorityKey` order, filtered by [SubscriptionOnboardingStepPlugin.shouldShow], and each
 * becomes a [SubscriptionOnboardingActivityStep]. Already-completed steps are skipped on re-entry via the
 * step precondition. Running off the end of the plan completes the run (the activity finishes to Settings).
 */
@SingleInstanceIn(AppScope::class)
class SubscriptionOnboardingPlanProvider @Inject constructor(
    private val stepPlugins: PluginPoint<SubscriptionOnboardingStepPlugin>,
    private val stepStore: SubscriptionOnboardingStepStore,
) {

    suspend fun buildPlan(): LinearOnboardingPlan {
        val steps = stepPlugins.getPlugins()
            .filter { it.shouldShow() }
            .map { stepPlugin -> activityStep(stepPlugin) }
        return LinearOnboardingPlan(
            id = SUBSCRIPTION_ONBOARDING_PLAN_ID,
            steps = steps,
        )
    }

    private fun activityStep(stepPlugin: SubscriptionOnboardingStepPlugin): SubscriptionOnboardingActivityStep =
        SubscriptionOnboardingActivityStep(
            id = stepPlugin.stepId,
            stepPlugin = stepPlugin,
            precondition = { !stepStore.isCompleted(stepPlugin.stepId) },
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
