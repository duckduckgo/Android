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

package com.duckduckgo.brokensite.impl

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.InternalAlwaysEnabled
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "brokenSitePrompt",
    settingsStore = BrokenSitePromptRCFeatureStore::class,
)
interface BrokenSitePromptRCFeature {
    @InternalAlwaysEnabled
    @DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@RemoteFeatureStoreNamed(BrokenSitePromptRCFeature::class)
class BrokenSitePromptRCFeatureStore @Inject constructor(
    private val repository: BrokenSiteReportRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : FeatureSettings.Store {

    private val jsonAdapter by lazy { buildJsonAdapter() }

    override fun store(jsonString: String) {
        appCoroutineScope.launch {
            jsonAdapter.fromJson(jsonString)?.let {
                repository.setBrokenSitePromptRCSettings(it.maxDismissStreak, it.dismissStreakResetDays, it.coolDownDays)
            }
        }
    }

    private fun buildJsonAdapter(): JsonAdapter<BrokenSitePromptSettings> {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return moshi.adapter(BrokenSitePromptSettings::class.java)
    }

    data class BrokenSitePromptSettings(
        val maxDismissStreak: Int,
        val dismissStreakResetDays: Int,
        val coolDownDays: Int,
    )
}
