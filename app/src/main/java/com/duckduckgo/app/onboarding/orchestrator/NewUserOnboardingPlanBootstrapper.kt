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

import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.OnboardingSkipper
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val userStageStore: UserStageStore,
    private val onboardingSkipper: OnboardingSkipper,
) {

    private val mutex = Mutex()

    /**
     * Starts the orchestrator-driven onboarding plan and returns the resulting [LinearOnboardingState].
     * The caller is responsible for only invoking this for a new user.
     */
    suspend fun startNewUserOnboardingPlan(): LinearOnboardingState.InProgress = mutex.withLock {
        orchestrator.startPlan(
            planProvider.buildRootPlan(
                onCompleted = { userStageStore.stageCompleted(AppStage.NEW) },
                onSkipped = { onboardingSkipper.markOnboardingAsCompleted() },
            ),
        )
        requireNotNull(orchestrator.state.value as? LinearOnboardingState.InProgress) {
            "New user onboarding plan needs to have at least one valid step"
        }
    }
}
