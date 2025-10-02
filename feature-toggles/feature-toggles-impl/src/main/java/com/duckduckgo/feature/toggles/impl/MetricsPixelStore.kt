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
import com.duckduckgo.feature.toggles.api.PixelDefinition
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface MetricsPixelStore {
    /**
     * @return true if the pixel with the given tag can be fired or false otherwise
     */
    suspend fun wasPixelFired(tag: String): Boolean

    /**
     * Stores the tag [String] passed as parameter
     */
    fun storePixelTag(tag: String)

    /**
     * Increases the count of searches for the [featureName] passed as parameter
     */
    suspend fun increaseMetricForPixelDefinition(definition: PixelDefinition, metric: RetentionMetric): Int

    /**
     * Returns the number [Int] of app use for the given [featureName]
     */
    suspend fun getMetricForPixelDefinition(definition: PixelDefinition, metric: RetentionMetric): Int
}

enum class RetentionMetric {
    SEARCH,
    APP_USE,
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

    override suspend fun increaseMetricForPixelDefinition(definition: PixelDefinition, metric: RetentionMetric) =
        withContext(dispatcherProvider.io()) {
            val tag = "${definition}_$metric"
            val count = preferences.getInt(tag, 0)
            preferences.edit {
                putInt(tag, count + 1)
                apply()
            }
            preferences.getInt(tag, 0)
        }

    override suspend fun getMetricForPixelDefinition(definition: PixelDefinition, metric: RetentionMetric): Int {
        val tag = "${definition}_$metric"
        return withContext(dispatcherProvider.io()) {
            preferences.getInt(tag, 0)
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.feature.toggles.pixels.prefs"
    }
}
