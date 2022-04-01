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

package com.duckduckgo.privacy.config.impl.features.autofill

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureName
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.features.privacyFeatureValueOf
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.AutofillExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.autofill.AutofillRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

@ContributesMultibinding(AppScope::class)
class AutofillPlugin @Inject constructor(
    private val autofillRepository: AutofillRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository
) : PrivacyFeaturePlugin {

    override fun store(
        name: FeatureName,
        jsonString: String
    ): Boolean {
        @Suppress("NAME_SHADOWING")
        val name = privacyFeatureValueOf(name.value)
        if (name == featureName) {
            val autofillExceptions = mutableListOf<AutofillExceptionEntity>()
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<AutofillFeature> =
                moshi.adapter(AutofillFeature::class.java)

            val httpsFeature: AutofillFeature? = jsonAdapter.fromJson(jsonString)
            httpsFeature?.exceptions?.map {
                autofillExceptions.add(AutofillExceptionEntity(it.domain, it.reason))
            }
            autofillRepository.updateAll(autofillExceptions)
            val isEnabled = httpsFeature?.state == "enabled"
            privacyFeatureTogglesRepository.insert(PrivacyFeatureToggles(name, isEnabled))
            return true
        }
        return false
    }

    override val featureName: PrivacyFeatureName = PrivacyFeatureName.AutofillFeatureName
}
