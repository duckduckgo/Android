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
 * Persists subscription onboarding progress across sessions: which steps have been completed. The
 * flow resumes at the first step that isn't completed, so this is all the state it needs.
 */
@SingleInstanceIn(AppScope::class)
class SubscriptionOnboardingDataStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    val completedSteps: Set<String>
        get() = preferences.getStringSet(KEY_COMPLETED_STEPS, emptySet()).orEmpty()

    fun markCompleted(stepName: String) {
        preferences.edit { putStringSet(KEY_COMPLETED_STEPS, completedSteps + stepName) }
    }

    fun isCompleted(stepName: String): Boolean = completedSteps.contains(stepName)

    companion object {
        const val FILENAME = "com.duckduckgo.subscriptions.onboarding"
        private const val KEY_COMPLETED_STEPS = "KEY_COMPLETED_STEPS"
    }
}
