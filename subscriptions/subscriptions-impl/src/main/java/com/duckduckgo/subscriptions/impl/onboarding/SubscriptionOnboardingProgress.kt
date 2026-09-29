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
import com.duckduckgo.pir.api.PirFeature
import com.duckduckgo.pir.api.dashboard.PirFeatureState
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.onboarding.completion.SubscriptionOnboardingCompletionViewModel.Companion.PIR_ROW_ID
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStepStore
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Single source of "how far through subscription onboarding the user is", 0..100. Mirrors the completion
 * screen's calculation — the steps that contribute a completion-summary row, plus PIR when the user is
 * entitled and eligible — so the settings entry-point card always shows the same number the completion
 * screen did.
 */
@SingleInstanceIn(AppScope::class)
class SubscriptionOnboardingProgress @Inject constructor(
    private val stepPlugins: PluginPoint<SubscriptionOnboardingStepPlugin>,
    private val stepStore: SubscriptionOnboardingStepStore,
    private val subscriptions: Subscriptions,
    private val pirFeature: PirFeature,
) {

    suspend fun completionPercentage(): Int {
        val stepIds = stepPlugins.getPlugins()
            .filter { it.shouldShow() }
            .filter { it.completionSummaryRow != null }
            .map { it.stepId }

        val entitled = Product.PIR in subscriptions.getEntitlementStatus().firstOrNull().orEmpty()
        val showPir = entitled && pirFeature.getPirFeatureState() == PirFeatureState.ENABLED
        val allStepIds = if (showPir) stepIds + PIR_ROW_ID else stepIds

        if (allStepIds.isEmpty()) return 0
        return allStepIds.count { stepStore.isCompleted(it) } * 100 / allStepIds.size
    }
}
