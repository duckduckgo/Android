/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.cache

import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeatureModels.AutoconsentSettings
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AutoconsentSettingsCache {
    fun getSettings(): AutoconsentSettings?
    fun updateSettings(settingsJson: String)
    fun getHash(): Int
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = AutoconsentSettingsCache::class,
)
@SingleInstanceIn(AppScope::class)
class RealAutoconsentSettingsCache @Inject constructor() : AutoconsentSettingsCache {

    private var hash = 0
    private var settings: AutoconsentSettings? = null

    override fun getSettings(): AutoconsentSettings? {
        return settings
    }

    override fun updateSettings(settingsJson: String) {
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val settingsAdapter = moshi.adapter(AutoconsentSettings::class.java)
        settings = settingsAdapter.fromJson(settingsJson)
        hash = settingsJson.hashCode()
    }

    override fun getHash(): Int {
        return hash
    }
}
