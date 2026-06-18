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

import com.duckduckgo.app.onboarding.LinearOnboardingOrchestratorFeature
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.OnboardingSkipper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Builds and starts the linear onboarding plan via [NewUserOnboardingPlanProvider].
 *
 * Terminal callbacks bind the orchestrator's lifecycle to [AppStage]: the orchestrator awaits
 * them before emitting [Completed] / [Skipped], so the [AppStage] write is visible to any listener
 * that routes off the terminal state.
 */
@SingleInstanceIn(AppScope::class)
class NewUserOnboardingPlanBootstrapper @Inject constructor(
    private val orchestrator: LinearOnboardingOrchestrator,
    private val planProvider: NewUserOnboardingPlanProvider,
    private val orchestratorFeature: LinearOnboardingOrchestratorFeature,
    private val userStageStore: UserStageStore,
    private val onboardingSkipper: OnboardingSkipper,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val mutex = Mutex()

    /**
     * Starts the orchestrator-driven onboarding plan when the feature flag is enabled, returning the
     * resulting [OnboardingPlanStartResult]. The caller is responsible for only invoking this for a new
     * user. The root plan always has an eligible first step, so a started run is
     * [LinearOnboardingState.InProgress]; a non-InProgress result (a misconfigured plan with no eligible
     * steps) is treated as [OnboardingPlanStartResult.Disabled].
     */
    suspend fun startNewUserOnboardingPlanIfEnabled(): OnboardingPlanStartResult = mutex.withLock {
        val orchestratorEnabled = withContext(dispatcherProvider.io()) { orchestratorFeature.self().isEnabled() }
        if (!orchestratorEnabled) return@withLock OnboardingPlanStartResult.Disabled

        orchestrator.startPlan(
            planProvider.buildRootPlan(
                onCompleted = { userStageStore.stageCompleted(AppStage.NEW) },
                onSkipped = { onboardingSkipper.markOnboardingAsCompleted() },
            ),
        )
        when (val startState = orchestrator.state.value) {
            is LinearOnboardingState.InProgress -> OnboardingPlanStartResult.Enabled(currentState = startState)
            else -> OnboardingPlanStartResult.Disabled
        }
    }

    sealed interface OnboardingPlanStartResult {
        /**
         * Use legacy linear onboarding controller.
         */
        data object Disabled : OnboardingPlanStartResult

        /**
         * Use the [LinearOnboardingOrchestrator].
         */
        data class Enabled(val currentState: LinearOnboardingState.InProgress) : OnboardingPlanStartResult
    }
}
