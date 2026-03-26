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

package com.duckduckgo.user.agent.impl.remoteconfig

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(ClientBrandHintFeature::class)
class ClientBrandHintFeatureSettingsStore @Inject constructor(
    private val repository: ClientBrandHintFeatureSettingsRepository,
) : FeatureSettings.Store {

    private val jsonAdapter by lazy { buildJsonAdapter() }

    override fun store(jsonString: String) {
        logcat(VERBOSE) { "ClientBrandHintProvider: store $jsonString" }
        jsonAdapter.fromJson(jsonString)?.let {
            repository.updateAllSettings(it)
        }
    }

    private fun buildJsonAdapter(): JsonAdapter<ClientBrandHintSettings> {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return moshi.adapter(ClientBrandHintSettings::class.java)
    }
}
