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

package com.duckduckgo.app.fire.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/**
 * DataStore for managing Fire clearing preferences.
 * Stores two separate sets of clearing options:
 * - Manual options: Options for manual Fire button clearing
 * - Automatic options: Options used for automatic clearing on app exit
 */
interface FireDataStore {
    /**
     * Gets the flow of manual clear options for manual Fire actions.
     */
    fun getManualClearOptionsFlow(): Flow<Set<FireClearOption>>

    /**
     * Gets the manual clear options for manual Fire actions.
     */
    suspend fun getManualClearOptions(): Set<FireClearOption>

    /**
     * Sets the manual clear options for manual Fire actions.
     * @param options Set of options to clear. Can be any combination of TABS, DATA, and DUCKAI_CHATS.
     */
    suspend fun setManualClearOptions(options: Set<FireClearOption>)

    /**
     * Adds a clear option to the manual selection.
     */
    suspend fun addManualClearOption(option: FireClearOption)

    /**
     * Removes a clear option from the manual selection.
     */
    suspend fun removeManualClearOption(option: FireClearOption)

    /**
     * Checks if a specific option is in the manual selection.
     */
    suspend fun isManualClearOptionSelected(option: FireClearOption): Boolean

    /**
     * Gets the flow of automatic clear options.
     */
    fun getAutomaticClearOptionsFlow(): Flow<Set<FireClearOption>>

    /**
     * Gets the automatic clear options.
     */
    suspend fun getAutomaticClearOptions(): Set<FireClearOption>

    /**
     * Sets the automatic clear options.
     * @param options Set of options to clear automatically. Can be any combination of TABS, DATA, and DUCKAI_CHATS.
     */
    suspend fun setAutomaticClearOptions(options: Set<FireClearOption>)

    /**
     * Adds a clear option to the automatic selection.
     */
    suspend fun addAutomaticClearOption(option: FireClearOption)

    /**
     * Removes a clear option from the automatic selection.
     */
    suspend fun removeAutomaticClearOption(option: FireClearOption)

    /**
     * Checks if a specific option is in the automatic selection.
     */
    suspend fun isAutomaticClearOptionSelected(option: FireClearOption): Boolean

    /**
     * Gets the flow of automatic clear when option.
     */
    fun getAutomaticallyClearWhenOptionFlow(): Flow<ClearWhenOption>

    /**
     * Gets the automatic clear when option.
     */
    suspend fun getAutomaticallyClearWhenOption(): ClearWhenOption

    /**
     * Sets the automatic clear when option.
     * @param option The option determining when to automatically clear data.
     */
    suspend fun setAutomaticallyClearWhenOption(option: ClearWhenOption)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesFireDataStore @Inject constructor(
    @FireData private val store: DataStore<Preferences>,
    private val settingsDataStore: SettingsDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : FireDataStore {

    private companion object {
        val KEY_MANUAL_CLEAR_OPTIONS = stringSetPreferencesKey(name = "MANUAL_CLEAR_OPTIONS")
        val KEY_AUTOMATIC_CLEAR_OPTIONS = stringSetPreferencesKey(name = "AUTOMATIC_CLEAR_OPTIONS")
        val KEY_AUTOMATIC_CLEAR_WHEN_OPTION = stringPreferencesKey(name = "AUTOMATIC_CLEAR_WHEN_OPTION")
        val DEFAULT_OPTIONS = setOf(FireClearOption.TABS, FireClearOption.DATA)
    }

    private suspend fun getLegacyOptions(): Set<FireClearOption> = withContext(dispatcherProvider.io()) {
        buildSet {
            when (settingsDataStore.automaticallyClearWhatOption) {
                ClearWhatOption.CLEAR_NONE -> Unit
                ClearWhatOption.CLEAR_TABS_ONLY -> add(FireClearOption.TABS)
                ClearWhatOption.CLEAR_TABS_AND_DATA -> {
                    add(FireClearOption.TABS)
                    add(FireClearOption.DATA)
                }
            }
            if (settingsDataStore.clearDuckAiData) {
                add(FireClearOption.DUCKAI_CHATS)
            }
        }
    }

    private suspend fun getLegacyWhenOption(): ClearWhenOption = withContext(dispatcherProvider.io()) {
        settingsDataStore.automaticallyClearWhenOption
    }

    override fun getManualClearOptionsFlow(): Flow<Set<FireClearOption>> {
        return store.data.map { preferences ->
            val stringSet = preferences[KEY_MANUAL_CLEAR_OPTIONS] ?: getDefaultManualOptions().map { it.name }.toSet()
            parseOptionsFromStrings(stringSet)
        }
    }

    override suspend fun getManualClearOptions(): Set<FireClearOption> {
        return getManualClearOptionsFlow().firstOrNull() ?: getDefaultManualOptions()
    }

    private suspend fun getDefaultManualOptions(): Set<FireClearOption> = withContext(dispatcherProvider.io()) {
        buildSet {
            addAll(DEFAULT_OPTIONS)
            if (settingsDataStore.clearDuckAiData) {
                add(FireClearOption.DUCKAI_CHATS)
            }
        }
    }

    override suspend fun setManualClearOptions(options: Set<FireClearOption>) {
        store.edit { preferences ->
            preferences[KEY_MANUAL_CLEAR_OPTIONS] = options.map { it.name }.toSet()
        }
    }

    override suspend fun addManualClearOption(option: FireClearOption) {
        store.edit { preferences ->
            val currentOptions = preferences[KEY_MANUAL_CLEAR_OPTIONS] ?: getDefaultManualOptions().map { it.name }.toSet()
            preferences[KEY_MANUAL_CLEAR_OPTIONS] = currentOptions + option.name
        }
    }

    override suspend fun removeManualClearOption(option: FireClearOption) {
        store.edit { preferences ->
            val currentOptions = preferences[KEY_MANUAL_CLEAR_OPTIONS] ?: getDefaultManualOptions().map { it.name }.toSet()
            preferences[KEY_MANUAL_CLEAR_OPTIONS] = currentOptions - option.name
        }
    }

    override suspend fun isManualClearOptionSelected(option: FireClearOption): Boolean {
        return getManualClearOptions().contains(option)
    }

    override fun getAutomaticClearOptionsFlow(): Flow<Set<FireClearOption>> {
        return store.data.map { preferences ->
            val stringSet = preferences[KEY_AUTOMATIC_CLEAR_OPTIONS] ?: getLegacyOptions().map { it.name }.toSet()
            parseOptionsFromStrings(stringSet)
        }
    }

    override suspend fun getAutomaticClearOptions(): Set<FireClearOption> {
        return getAutomaticClearOptionsFlow().firstOrNull() ?: getLegacyOptions()
    }

    override suspend fun setAutomaticClearOptions(options: Set<FireClearOption>) {
        store.edit { preferences ->
            preferences[KEY_AUTOMATIC_CLEAR_OPTIONS] = options.map { it.name }.toSet()
        }
    }

    override suspend fun addAutomaticClearOption(option: FireClearOption) {
        store.edit { preferences ->
            val currentOptions = preferences[KEY_AUTOMATIC_CLEAR_OPTIONS] ?: getLegacyOptions().map { it.name }.toSet()
            preferences[KEY_AUTOMATIC_CLEAR_OPTIONS] = currentOptions + option.name
        }
    }

    override suspend fun removeAutomaticClearOption(option: FireClearOption) {
        store.edit { preferences ->
            val currentOptions = preferences[KEY_AUTOMATIC_CLEAR_OPTIONS] ?: getLegacyOptions().map { it.name }.toSet()
            preferences[KEY_AUTOMATIC_CLEAR_OPTIONS] = currentOptions - option.name
        }
    }

    override suspend fun isAutomaticClearOptionSelected(option: FireClearOption): Boolean {
        return getAutomaticClearOptions().contains(option)
    }

    override fun getAutomaticallyClearWhenOptionFlow(): Flow<ClearWhenOption> {
        return store.data.map { preferences ->
            val savedValue = preferences[KEY_AUTOMATIC_CLEAR_WHEN_OPTION]
            if (savedValue != null) {
                try {
                    ClearWhenOption.valueOf(savedValue)
                } catch (e: IllegalArgumentException) {
                    getLegacyWhenOption()
                }
            } else {
                getLegacyWhenOption()
            }
        }
    }

    override suspend fun getAutomaticallyClearWhenOption(): ClearWhenOption {
        return getAutomaticallyClearWhenOptionFlow().firstOrNull() ?: getLegacyWhenOption()
    }

    override suspend fun setAutomaticallyClearWhenOption(option: ClearWhenOption) {
        store.edit { preferences ->
            preferences[KEY_AUTOMATIC_CLEAR_WHEN_OPTION] = option.name
        }
    }

    private fun parseOptionsFromStrings(stringSet: Set<String>): Set<FireClearOption> {
        return stringSet.mapNotNull { name ->
            try {
                FireClearOption.valueOf(name)
            } catch (e: IllegalArgumentException) {
                logcat { "Invalid FireClearOption value: $name" }
                null
            }
        }.toSet()
    }
}
