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

package com.duckduckgo.app.onboarding.ui

import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import javax.inject.Inject

/** Set only from internal builds, so release builds always read the View dialogs. */
class ComposeOnboardingDialogsStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {

    private val preferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }

    var useComposeDialogs: Boolean
        get() = preferences.getBoolean(KEY_USE_COMPOSE_DIALOGS, false)
        set(value) = preferences.edit { putBoolean(KEY_USE_COMPOSE_DIALOGS, value) }

    fun consumeUseComposeDialogs(): Boolean = useComposeDialogs.also { useComposeDialogs = false }

    private companion object {
        const val FILENAME = "com.duckduckgo.app.onboarding.compose.dialogs"
        const val KEY_USE_COMPOSE_DIALOGS = "USE_COMPOSE_DIALOGS"
    }
}
