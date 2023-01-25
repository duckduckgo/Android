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

package com.duckduckgo.autofill.impl.plugins

import com.duckduckgo.autofill.api.AutofillFeatureName
import com.duckduckgo.autofill.impl.AutofillFeature
import com.duckduckgo.autofill.impl.autofillFeatureValueOf
import com.duckduckgo.autofill.store.AutofillExceptionEntity
import com.duckduckgo.autofill.store.AutofillFeatureToggleRepository
import com.duckduckgo.autofill.store.AutofillFeatureToggles
import com.duckduckgo.autofill.store.AutofillRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AutofillPlugin @Inject constructor(
    private val autofillRepository: AutofillRepository,
    private val autofillFeatureToggleRepository: AutofillFeatureToggleRepository,
) : PrivacyFeaturePlugin {

    override fun store(
        featureName: String,
        jsonString: String,
    ): Boolean {
        val autofillFeatureName = autofillFeatureValueOf(featureName) ?: return false
        if (autofillFeatureName.value == this.featureName) {
            val autofillExceptions = mutableListOf<AutofillExceptionEntity>()
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<AutofillFeature> =
                moshi.adapter(AutofillFeature::class.java)

            val autofillFeature: AutofillFeature? = jsonAdapter.fromJson(jsonString)
            autofillFeature?.exceptions?.map {
                autofillExceptions.add(AutofillExceptionEntity(it.domain, it.reason))
            }
            autofillRepository.updateAll(autofillExceptions)
            val isEnabled = autofillFeature?.state == "enabled"
            autofillFeatureToggleRepository.insert(AutofillFeatureToggles(autofillFeatureName, isEnabled, autofillFeature?.minSupportedVersion))
            return true
        }
        return false
    }

    override val featureName: String = AutofillFeatureName.Autofill.value
}
