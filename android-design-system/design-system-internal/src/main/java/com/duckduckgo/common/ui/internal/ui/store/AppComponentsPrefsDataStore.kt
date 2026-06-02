/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.internal.ui.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoThemeVariant
import com.duckduckgo.common.ui.isInNightMode
import com.duckduckgo.common.ui.store.ThemingSharedPreferences
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal val Context.appComponentsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "com.duckduckgo.app.dev_settings_activity.theme_settings",
)

/**
 * DataStore implementation to persist and observe theme changes for the AppComponents sample app.
 */
class AppComponentsPrefsDataStore(
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
    private val store: DataStore<Preferences>,
    private val themePrefMapper: ThemingSharedPreferences.ThemePrefsMapper,
) {

    val themeFlow: Flow<DuckDuckGoTheme> = store.data.map { preferences ->
        val savedValue = preferences[stringPreferencesKey(KEY_SELECTED_THEME)]
        themePrefMapper.themeFrom(
            savedValue,
            DuckDuckGoTheme.SYSTEM_DEFAULT,
            context.isInNightMode(),
        )
    }.flowOn(dispatcherProvider.io())

    suspend fun setTheme(theme: DuckDuckGoTheme) {
        withContext(dispatcherProvider.io()) {
            store.edit { preferences ->
                preferences[stringPreferencesKey(KEY_SELECTED_THEME)] =
                    themePrefMapper.prefValue(theme)
            }
        }
    }

    val variantFlow: Flow<DuckDuckGoThemeVariant> = store.data.map { preferences ->
        val savedValue = preferences[stringPreferencesKey(KEY_SELECTED_VARIANT)]
        runCatching { DuckDuckGoThemeVariant.valueOf(savedValue ?: "") }
            .getOrDefault(DuckDuckGoThemeVariant.Default)
    }.flowOn(dispatcherProvider.io())

    suspend fun setVariant(variant: DuckDuckGoThemeVariant) {
        withContext(dispatcherProvider.io()) {
            store.edit { preferences ->
                preferences[stringPreferencesKey(KEY_SELECTED_VARIANT)] = variant.name
            }
        }
    }

    companion object {
        const val KEY_SELECTED_THEME = "KEY_SELECTED_THEME"
        const val KEY_SELECTED_VARIANT = "KEY_SELECTED_VARIANT"
    }
}
