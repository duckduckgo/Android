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

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class DuckAiMigrationPrefs @Inject constructor(
    sharedPreferencesProvider: SharedPreferencesProvider,
) {
    private val prefs by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFS_NAME)
    }

    fun isMigrationDone(key: String): Boolean = prefs.getBoolean(key, false)

    fun markMigrationDone(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }

    fun reset(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun resetAll() {
        prefs.edit().clear().apply()
    }

    fun getAll(): Map<String, *> = prefs.all

    companion object {
        const val CHATS_KEY = "chats"
        private const val PREFS_NAME = "duck_ai_migration_prefs"
    }
}
