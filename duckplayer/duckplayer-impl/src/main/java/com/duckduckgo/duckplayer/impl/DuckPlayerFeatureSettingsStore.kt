/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.duckplayer.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(DuckPlayerFeature::class)
class DuckPlayerFatureSettingsStore @Inject constructor(
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : FeatureSettings.Store {

    private val jsonAdapter by lazy { buildJsonAdapter() }

    override fun store(jsonString: String) {
        coroutineScope.launch(dispatcherProvider.io()) {
            try {
                jsonAdapter.fromJson(jsonString)?.let {
                    duckPlayerFeatureRepository.storeDuckPlayerDisabledHelpPageLink(it.duckPlayerDisabledHelpPageLink)
                    duckPlayerFeatureRepository.storeYouTubePath(it.youtubePath)
                    duckPlayerFeatureRepository.storeYoutubeEmbedUrl(it.youtubeEmbedUrl)
                    duckPlayerFeatureRepository.storeYouTubeUrl(it.youTubeUrl)
                    duckPlayerFeatureRepository.storeYouTubeReferrerHeaders(it.youTubeReferrerHeaders)
                    duckPlayerFeatureRepository.storeYouTubeReferrerQueryParams(it.youTubeReferrerQueryParams)
                    duckPlayerFeatureRepository.storeYouTubeVideoIDQueryParam(it.youTubeVideoIDQueryParam)
                } ?: run {
                    // If no help link page present, we clear the stored one
                    duckPlayerFeatureRepository.storeDuckPlayerDisabledHelpPageLink(null)
                }
            } catch (e: Exception) {
                logcat { "Failed to store DuckPlayer settings: ${e.asLog()}" }
                // If no help link page present, we clear the stored one
                duckPlayerFeatureRepository.storeDuckPlayerDisabledHelpPageLink(null)
            }
        }
    }

    private fun buildJsonAdapter(): JsonAdapter<DuckPlayerSetting> {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return moshi.adapter(DuckPlayerSetting::class.java)
    }
}

@JsonClass(generateAdapter = true)
data class DuckPlayerSetting(
    @field:Json(name = "duckPlayerDisabledHelpPageLink")
    val duckPlayerDisabledHelpPageLink: String?,
    @field:Json(name = "youtubePath")
    val youtubePath: String,
    @field:Json(name = "youtubeEmbedUrl")
    val youtubeEmbedUrl: String,
    @field:Json(name = "youTubeUrl")
    val youTubeUrl: String,
    @field:Json(name = "youTubeReferrerHeaders")
    val youTubeReferrerHeaders: List<String>,
    @field:Json(name = "youTubeReferrerQueryParams")
    val youTubeReferrerQueryParams: List<String>,
    @field:Json(name = "youTubeVideoIDQueryParams")
    val youTubeVideoIDQueryParam: String,

)
