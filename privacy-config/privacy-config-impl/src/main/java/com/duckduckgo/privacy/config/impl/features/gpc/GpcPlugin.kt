/*
 * Copyright (c) 2021 DuckDuckGo
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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.impl.features.privacyFeatureValueOf
import com.duckduckgo.privacy.config.store.GpcContentScopeConfigEntity
import com.duckduckgo.privacy.config.store.GpcExceptionEntity
import com.duckduckgo.privacy.config.store.GpcHeaderEnabledSiteEntity
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class GpcPlugin @Inject constructor(
    private val gpcRepository: GpcRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository,
) : PrivacyFeaturePlugin {

    override fun store(
        featureName: String,
        jsonString: String,
    ): Boolean {
        @Suppress("NAME_SHADOWING")
        val privacyFeature = privacyFeatureValueOf(featureName) ?: return false
        if (privacyFeature.value == this.featureName) {
            val gpcExceptions = mutableListOf<GpcExceptionEntity>()
            val gpcHeaders = mutableListOf<GpcHeaderEnabledSiteEntity>()
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<GpcFeature> =
                moshi.adapter(GpcFeature::class.java)

            val gpcFeature: GpcFeature? = jsonAdapter.fromJson(jsonString)
            gpcFeature?.exceptions?.map {
                gpcExceptions.add(GpcExceptionEntity(it.domain))
            }
            gpcFeature?.settings?.gpcHeaderEnabledSites?.map {
                gpcHeaders.add(GpcHeaderEnabledSiteEntity(it))
            }
            gpcRepository.updateAll(gpcExceptions, gpcHeaders, GpcContentScopeConfigEntity(config = jsonString))
            val isEnabled = gpcFeature?.state == "enabled"
            privacyFeatureTogglesRepository.insert(PrivacyFeatureToggles(this.featureName, isEnabled, gpcFeature?.minSupportedVersion))
            return true
        }
        return false
    }

    override val featureName: String = PrivacyFeatureName.GpcFeatureName.value
}
