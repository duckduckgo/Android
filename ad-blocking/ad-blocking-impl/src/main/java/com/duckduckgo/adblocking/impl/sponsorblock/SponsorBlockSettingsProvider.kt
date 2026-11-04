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

package com.duckduckgo.adblocking.impl.sponsorblock

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

data class SponsorBlockSettings(
    val categories: List<String> = DEFAULT_CATEGORIES,
    val actionTypes: List<String> = DEFAULT_ACTION_TYPES,
) {
    companion object {
        val DEFAULT_CATEGORIES = listOf("sponsor")
        val DEFAULT_ACTION_TYPES = listOf("skip")
    }
}

interface SponsorBlockSettingsProvider {
    /** Settings from the sponsorBlock remote feature, falling back to defaults for anything missing or malformed. */
    suspend fun settings(): SponsorBlockSettings
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSponsorBlockSettingsProvider @Inject constructor(
    private val feature: SponsorBlockFeature,
    private val dispatchers: DispatcherProvider,
) : SponsorBlockSettingsProvider {

    private val settingsAdapter: JsonAdapter<SettingsJson> by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(SettingsJson::class.java)
    }

    override suspend fun settings(): SponsorBlockSettings = withContext(dispatchers.io()) {
        val json = feature.self().getSettings() ?: return@withContext SponsorBlockSettings()
        val parsed = runCatching { settingsAdapter.fromJson(json) }
            .onFailure { logcat(WARN) { "SponsorBlock: failed to parse settings: ${it.asLog()}" } }
            .getOrNull()
            ?: return@withContext SponsorBlockSettings()
        SponsorBlockSettings(
            categories = parsed.categories ?: SponsorBlockSettings.DEFAULT_CATEGORIES,
            actionTypes = parsed.actionTypes ?: SponsorBlockSettings.DEFAULT_ACTION_TYPES,
        )
    }

    private data class SettingsJson(
        @field:Json(name = "categories") val categories: List<String>?,
        @field:Json(name = "actionTypes") val actionTypes: List<String>?,
    )
}
