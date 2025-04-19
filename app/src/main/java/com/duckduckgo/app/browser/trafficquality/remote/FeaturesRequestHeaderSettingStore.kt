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

package com.duckduckgo.app.browser.trafficquality.remote

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

interface FeaturesRequestHeaderStore {
    fun getConfig(): List<TrafficQualityAppVersion>
}

data class TrafficQualitySettingsJson(
    val versions: List<TrafficQualityAppVersion>,
)

data class TrafficQualityAppVersion(
    val appVersion: Int,
    val daysUntilLoggingStarts: Int,
    val daysLogging: Int,
    val featuresLogged: TrafficQualityAppVersionFeatures,
)

data class TrafficQualityAppVersionFeatures(
    val gpc: Boolean,
    val cpm: Boolean,
    val appTP: Boolean,
    val netP: Boolean,
)

@ContributesBinding(AppScope::class)
class FeaturesRequestHeaderSettingStore @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val moshi: Moshi,
) : FeaturesRequestHeaderStore {

    private val jsonAdapter: JsonAdapter<TrafficQualitySettingsJson> by lazy {
        moshi.adapter(TrafficQualitySettingsJson::class.java)
    }

    override fun getConfig(): List<TrafficQualityAppVersion> {
        val config = androidBrowserConfigFeature.featuresRequestHeader().getSettings()?.let {
            runCatching {
                val configJson = jsonAdapter.fromJson(it)
                configJson?.versions
            }.getOrDefault(emptyList())
        } ?: emptyList()
        return config
    }
}
