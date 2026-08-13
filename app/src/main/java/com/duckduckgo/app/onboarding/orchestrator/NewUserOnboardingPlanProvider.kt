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

package com.duckduckgo.app.onboarding.orchestrator

import com.duckduckgo.app.onboarding.CustomAiOnboardingResolver
import com.duckduckgo.app.onboarding.OnboardingPromptsExperimentManager
import com.duckduckgo.app.onboarding.SegmentedOnboardingExperimentManager
import com.duckduckgo.app.onboarding.SegmentedOnboardingExperimentManager.SegmentedOnboardingExperimentVariant
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Entry point for the new-user linear-onboarding plan. Resolves which flow applies — custom AI,
 * onboarding-prompts experiment, segmented onboarding, or the default — and delegates plan
 * composition to that flow's builder.
 *
 * Builders create a fresh [NewUserOnboardingPlanContext] and per-run [SuspendMemo]s on each build
 * call, so a new onboarding run never reads stale state.
 */
@SingleInstanceIn(AppScope::class)
class NewUserOnboardingPlanProvider @Inject internal constructor(
    private val appBuildConfig: AppBuildConfig,
    private val customAiOnboardingResolver: CustomAiOnboardingResolver,
    private val onboardingPromptsExperimentManager: OnboardingPromptsExperimentManager,
    private val segmentedOnboardingExperimentManager: SegmentedOnboardingExperimentManager,
    private val defaultPlanBuilder: DefaultOnboardingPlanBuilder,
    private val customAiPlanBuilder: CustomAiOnboardingPlanBuilder,
    private val segmentedPlanBuilder: SegmentedOnboardingPlanBuilder,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun buildRootPlan(
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan =
        if (customAiOnboardingResolver.resolve()) {
            customAiPlanBuilder.build(onCompleted, onSkipped)
        } else {
            val isReinstall = withContext(dispatchers.io()) { appBuildConfig.isAppReinstall() }
            val onboardingPromptExperimentVariant = if (isReinstall) {
                null
            } else {
                onboardingPromptsExperimentManager.enroll()
            }
            when {
                onboardingPromptExperimentVariant != null ->
                    defaultPlanBuilder.build(onCompleted, onSkipped, onboardingPromptExperimentVariant)
                segmentedOnboardingExperimentManager.enroll() == SegmentedOnboardingExperimentVariant.TREATMENT ->
                    segmentedPlanBuilder.build(onCompleted, onSkipped)
                else -> defaultPlanBuilder.build(onCompleted, onSkipped)
            }
        }
}
