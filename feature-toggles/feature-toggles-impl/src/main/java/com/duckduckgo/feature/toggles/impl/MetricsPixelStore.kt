/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.feature.toggles.impl

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface MetricsPixelStore {
    /**
     * @return true if the pixel with the given tag can be fired or false otherwise
     */
    suspend fun wasPixelFired(tag: String): Boolean

    /**
     * Stores the tag [String] passed as parameter
     */
    fun storePixelTag(tag: String)
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = MetricsPixelStore::class,
)
class RealMetricsPixelStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MetricsPixelStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    override fun storePixelTag(tag: String) {
        coroutineScope.launch(dispatcherProvider.io()) {
            preferences.edit { putBoolean(tag, true) }
        }
    }

    override suspend fun wasPixelFired(tag: String): Boolean {
        return withContext(dispatcherProvider.io()) {
            preferences.getBoolean(tag, false)
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.feature.toggles.pixels.prefs"
    }
}
