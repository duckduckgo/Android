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

import com.duckduckgo.app.onboarding.orchestrator.DeviceMigrationPlanProvider.Companion.MIGRATION_PLAN_ID
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanProvider.Companion.ROOT_PLAN_ID
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.isNewUser
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * The single place that decides which onboarding run, if any, the app owes the user: the device migration flow
 * first, then the new-user plan.
 *
 * While the migration experience is switched off remotely the flow is not started but stays pending, so it runs
 * once if the switch is turned back on.
 *
 * The migration plan's callback cannot start the new-user plan itself (the orchestrator rejects re-entrant calls
 * from plan callbacks), so whoever observes the migration run ending calls [startPending] again to hand off.
 */
@SingleIn(AppScope::class)
class PendingOnboardingStarter @Inject constructor(
    private val orchestrator: LinearOnboardingOrchestrator,
    private val deviceMigration: DeviceMigration,
    private val deviceMigrationPlanProvider: DeviceMigrationPlanProvider,
    private val userStageStore: UserStageStore,
    private val newUserOnboardingPlanBootstrapper: NewUserOnboardingPlanBootstrapper,
) {

    private val mutex = Mutex()

    /**
     * Returns the run the user should be in now, starting it if needed, or null when nothing is owed.
     *
     * Also null while another flow's run is in progress: the orchestrator cannot start a plan until it ends, and
     * whatever is owed is picked up on a later call.
     */
    suspend fun startPending(): LinearOnboardingState.InProgress? = mutex.withLock {
        val current = orchestrator.state.value as? LinearOnboardingState.InProgress
        if (current != null) return@withLock current.takeIf { it.rootPlanId in OWNED_PLAN_IDS }

        when {
            deviceMigration.isMigrationFlowPending() && deviceMigration.isMigrationExperienceEnabled() -> startMigrationPlan()
            userStageStore.isNewUser() -> newUserOnboardingPlanBootstrapper.startNewUserOnboardingPlan()
            else -> null
        }
    }

    private suspend fun startMigrationPlan(): LinearOnboardingState.InProgress? {
        orchestrator.startPlan(
            deviceMigrationPlanProvider.buildPlan(onCompleted = { deviceMigration.markMigrationFlowCompleted() }),
        )
        return orchestrator.state.value as? LinearOnboardingState.InProgress
    }

    private companion object {
        val OWNED_PLAN_IDS = setOf(MIGRATION_PLAN_ID, ROOT_PLAN_ID)
    }
}
