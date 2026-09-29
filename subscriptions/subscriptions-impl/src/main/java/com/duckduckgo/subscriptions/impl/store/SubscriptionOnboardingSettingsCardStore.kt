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
 * Durable count of how many times the onboarding entry-point card was shown in a completed (100%) state.
 * Once onboarding is complete the card is shown for only [MAX_COMPLETE_CARD_VIEWS] more appearances of the
 * settings screen, then never again.
 */
@SingleInstanceIn(AppScope::class)
class SubscriptionOnboardingSettingsCardStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    fun completeCardViews(): Int = preferences.getInt(KEY_COMPLETE_CARD_VIEWS, 0)

    fun incrementCompleteCardViews() {
        preferences.edit { putInt(KEY_COMPLETE_CARD_VIEWS, completeCardViews() + 1) }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.subscriptions.onboarding.settings.card"
        const val KEY_COMPLETE_CARD_VIEWS = "KEY_COMPLETE_CARD_VIEWS"
        const val MAX_COMPLETE_CARD_VIEWS = 2
    }
}
