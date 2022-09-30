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

package com.duckduckgo.privacy.config.impl.features.gpc

import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.features.privacyFeatureValueOf
import com.duckduckgo.privacy.config.store.GpcExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import okhttp3.internal.trimSubstring

@ContributesMultibinding(AppScope::class)
class GpcContentScopeConfigPlugin @Inject constructor(
    private val gpcRepository: GpcRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository
) : ContentScopeConfigPlugin {

    override fun config(): String? {
        @Suppress("NAME_SHADOWING")
        val privacyFeature = privacyFeatureValueOf(featureName) ?: return null
        if (privacyFeature.value == this.featureName) {
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<Gpc> =
                moshi.adapter(Gpc::class.java)
            val gpcFeature = GpcFeature(
                state = getJsonBoolean(gpcRepository.isGpcEnabled()),
                minSupportedVersion = privacyFeatureTogglesRepository.getMinSupportedVersion(privacyFeature),
                exceptions = gpcRepository.exceptions.map { GpcExceptionEntity(it.domain) },
                settings = GpcSettings(gpcHeaderEnabledSites = gpcRepository.headerEnabledSites.map { it.domain })
            )
            val json = jsonAdapter.toJson(Gpc(gpc = gpcFeature))
            return json.trimSubstring(1, json.length - 1)
        }
        return null
    }

    private fun getJsonBoolean(boolean: Boolean): String {
        return if (boolean) "enabled" else "disabled"
    }

    override val featureName: String = PrivacyFeatureName.GpcFeatureName.value
}

data class Gpc(
    val gpc: GpcFeature
)
