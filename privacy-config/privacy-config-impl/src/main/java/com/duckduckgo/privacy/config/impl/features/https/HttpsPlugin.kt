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

package com.duckduckgo.privacy.config.impl.features.https

import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.HttpsExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.https.HttpsRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

@ContributesMultibinding(AppObjectGraph::class)
class HttpsPlugin @Inject constructor(
    private val httpsRepository: HttpsRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository
) : PrivacyFeaturePlugin {

    override fun store(name: String, jsonString: String): Boolean {
        if (name == featureName.value) {
            val httpsExceptions = mutableListOf<HttpsExceptionEntity>()
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<HttpsFeature> =
                moshi.adapter(HttpsFeature::class.java)

            val httpsFeature: HttpsFeature? = jsonAdapter.fromJson(jsonString)
            httpsFeature?.exceptions?.map {
                httpsExceptions.add(HttpsExceptionEntity(it.domain, it.reason))
            }
            httpsRepository.updateAll(httpsExceptions)
            val isEnabled = httpsFeature?.state == "enabled"
            privacyFeatureTogglesRepository.insert(PrivacyFeatureToggles(name, isEnabled))
            return true
        }
        return false
    }

    override val featureName: PrivacyFeatureName = PrivacyFeatureName.HttpsFeatureName()
}
