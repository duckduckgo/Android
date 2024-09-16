/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.savedsites.store.FavoritesDisplayMode.NATIVE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

interface SavedSitesSettingsStore {
    var favoritesDisplayMode: FavoritesDisplayMode
    fun favoritesFormFactorModeFlow(): Flow<FavoritesDisplayMode>
}

class SavedSitesSettingsSharedPrefStore(
    private val context: Context,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SavedSitesSettingsStore {

    private val favoritesFavoritesDisplayModeFlow = MutableSharedFlow<FavoritesDisplayMode>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            favoritesFavoritesDisplayModeFlow.emit(favoritesDisplayMode)
        }
    }

    override fun favoritesFormFactorModeFlow(): Flow<FavoritesDisplayMode> = favoritesFavoritesDisplayModeFlow

    override var favoritesDisplayMode: FavoritesDisplayMode
        get() {
            val storedValue = preferences.getString(
                KEY_FAVORITES_DISPLAY_MODE,
                NATIVE.value,
            )
            return FavoritesDisplayMode.values().firstOrNull { it.value == storedValue } ?: NATIVE
        }
        set(favouritesDisplayMode) {
            preferences.edit(commit = true) {
                putString(KEY_FAVORITES_DISPLAY_MODE, favouritesDisplayMode.value)
            }
            emitNewValue()
        }

    private fun emitNewValue() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            favoritesFavoritesDisplayModeFlow.emit(favoritesDisplayMode)
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.savedsites.settings"
        const val KEY_FAVORITES_DISPLAY_MODE = "KEY_FAVORITES_DISPLAY_MODE"
    }
}

enum class FavoritesDisplayMode(val value: String) {
    NATIVE("display_native"),
    UNIFIED("display_all"),
}
