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

package com.duckduckgo.settings.impl.serpsettings

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.settings.api.SerpSettingsDataProvider
import com.duckduckgo.settings.impl.serpsettings.pixel.SerpSettingsPixelName
import com.duckduckgo.settings.impl.serpsettings.pixel.fireSerpSettingsCountAndDaily
import com.duckduckgo.settings.impl.serpsettings.store.SerpSettingsDataStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSerpSettingsDataProvider @Inject constructor(
    private val serpSettingsDataStore: SerpSettingsDataStore,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
) : SerpSettingsDataProvider {

    override fun observeSetting(key: String): Flow<String?> =
        serpSettingsDataStore.observeSerpSettings()
            .map { raw -> raw.extractSetting(key) }
            .distinctUntilChanged()
            .flowOn(dispatcherProvider.io())

    override suspend fun setSetting(key: String, value: String) {
        withContext(dispatcherProvider.io()) {
            // Merge inside a single transaction so a concurrent SERP updateNativeSettings can't clobber this write.
            serpSettingsDataStore.updateSerpSettings { current ->
                val json = if (current.isNullOrEmpty()) {
                    JSONObject()
                } else {
                    runCatching { JSONObject(current) }.getOrElse {
                        // The stored blob couldn't be parsed; we discard it and start fresh.
                        pixel.fireSerpSettingsCountAndDaily(
                            countPixel = SerpSettingsPixelName.SERP_SETTINGS_SERIALIZATION_FAILED_COUNT,
                            dailyPixel = SerpSettingsPixelName.SERP_SETTINGS_SERIALIZATION_FAILED_DAILY,
                        )
                        JSONObject()
                    }
                }
                json.put(key, value)
                json.toString()
            }
        }
    }

    private fun String?.extractSetting(key: String): String? {
        if (this.isNullOrEmpty()) return null
        val json = runCatching { JSONObject(this) }.getOrNull() ?: return null
        if (!json.has(key)) return null
        return json.optString(key).takeIf { it.isNotEmpty() }
    }
}
