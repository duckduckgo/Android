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

package com.duckduckgo.voice.impl.remoteconfig

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(VoiceSearchFeature::class)
class VoiceSearchFeatureSettingStore @Inject constructor(
    private val voiceSearchFeatureRepository: VoiceSearchFeatureRepository,
    private val jsonAdapter: JsonAdapter<VoiceSearchSetting>,
) : FeatureSettings.Store {

    override fun store(jsonSettings: String) {
        jsonAdapter.fromJson(jsonSettings)?.let {
            voiceSearchFeatureRepository.updateAllExceptions(it.excludedManufacturers, it.minVersion)
        }
    }
}
