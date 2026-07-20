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

package com.duckduckgo.subscriptions.impl.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Durable record of which subscription-onboarding steps a user has completed, keyed by step id. Lives
 * outside the (in-memory) onboarding orchestrator so completion survives app kills and can be read by the
 * summary step and by step preconditions (to skip already-completed steps on re-entry).
 */
@SingleInstanceIn(AppScope::class)
class SubscriptionOnboardingStepStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    fun isCompleted(stepId: String): Boolean = completedSteps().contains(stepId)

    fun setCompleted(stepId: String) {
        preferences.edit { putStringSet(KEY_COMPLETED_STEPS, completedSteps() + stepId) }
    }

    fun completedSteps(): Set<String> =
        preferences.getStringSet(KEY_COMPLETED_STEPS, emptySet()) ?: emptySet()

    companion object {
        const val FILENAME = "com.duckduckgo.subscriptions.onboarding.steps"
        const val KEY_COMPLETED_STEPS = "KEY_COMPLETED_STEPS"
    }
}
