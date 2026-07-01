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

package com.duckduckgo.subscriptions.impl.ui.onboarding

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.STEP
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingDataStore
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Read-only access to the user's total (cross-session) onboarding progress, used by the summary
 * screen. Only [SubscriptionOnboardingStepType.STEP] screens count; the total is derived from the
 * number of registered step plugins.
 */
interface SubscriptionOnboardingProgress {

    /** Number of distinct steps the user has completed (across all sessions). */
    suspend fun completedStepCount(): Int

    /** Total number of real steps in the flow (registered STEP plugins). */
    suspend fun totalStepCount(): Int

    /** Completion as a 0..100 percentage. Returns 0 when there are no steps. */
    suspend fun completionPercent(): Int
}

@ContributesBinding(AppScope::class)
class RealSubscriptionOnboardingProgress @Inject constructor(
    private val stepPlugins: ActivePluginPoint<SubscriptionOnboardingStepPlugin>,
    private val dataStore: SubscriptionOnboardingDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : SubscriptionOnboardingProgress {

    override suspend fun completedStepCount(): Int = withContext(dispatcherProvider.io()) {
        val stepNames = stepNames()
        dataStore.completedSteps.count { it in stepNames }
    }

    override suspend fun totalStepCount(): Int = withContext(dispatcherProvider.io()) {
        stepNames().size
    }

    override suspend fun completionPercent(): Int = withContext(dispatcherProvider.io()) {
        val stepNames = stepNames()
        val total = stepNames.size
        if (total == 0) {
            0
        } else {
            val completed = dataStore.completedSteps.count { it in stepNames }
            completed * 100 / total
        }
    }

    private suspend fun stepNames(): Set<String> =
        stepPlugins.getPlugins().filter { it.stepType == STEP }.map { it.name }.toSet()
}
