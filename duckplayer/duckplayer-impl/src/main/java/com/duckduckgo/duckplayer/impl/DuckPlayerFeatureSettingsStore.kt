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
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(DuckPlayerFeature::class)
class DuckPlayerFatureSettingsStore @Inject constructor(
    private val voiceSearchFeatureRepository: DuckPlayerFeatureRepository,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : FeatureSettings.Store {

    private val jsonAdapter by lazy { buildJsonAdapter() }

    override fun store(jsonString: String) {
        coroutineScope.launch(dispatcherProvider.io()) {
            jsonAdapter.fromJson(jsonString)?.let {
                voiceSearchFeatureRepository.storeDuckPlayerDisabledHelpPageLink(it.duckPlayerDisabledHelpPageLink)
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
    val duckPlayerDisabledHelpPageLink: String,
)
