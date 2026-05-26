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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.isNewUser
import com.duckduckgo.app.onboarding.ui.OnboardingSkipper
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// :app owns the linear-onboarding lifecycle. On app start, if the user is still
// in AppStage.NEW, we ask the plan provider to build the main plan with terminal
// callbacks bound to UserStageStore / OnboardingSkipper and hand it to the
// orchestrator. The orchestrator awaits those callbacks before emitting
// Completed / Skipped, preserving the "AppStage write happens before any
// consumer sees the terminal state" ordering that the legacy in-VM flow had.
@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class OnboardingPlanBootstrapper @Inject constructor(
    private val orchestrator: LinearOnboardingOrchestrator,
    private val planProvider: LinearOnboardingPlanProvider,
    private val userStageStore: UserStageStore,
    private val onboardingSkipper: OnboardingSkipper,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    private val mutex = Mutex()

    override fun onCreate(owner: LifecycleOwner) {
        appScope.launch { engageIfNeeded() }
    }

    /** Idempotent. Returns true if linear onboarding is or has been started. */
    suspend fun engageIfNeeded(): Boolean = mutex.withLock {
        when (orchestrator.state.value) {
            LinearOnboardingState.NotStarted -> if (userStageStore.isNewUser()) {
                orchestrator.startPlan(
                    planProvider.buildMainPlan(
                        onCompleted = { userStageStore.stageCompleted(AppStage.NEW) },
                        onSkipped = { onboardingSkipper.markOnboardingAsCompleted() },
                    ),
                )
                true
            } else false
            is LinearOnboardingState.InProgress -> true
            else -> false
        }
    }
}
