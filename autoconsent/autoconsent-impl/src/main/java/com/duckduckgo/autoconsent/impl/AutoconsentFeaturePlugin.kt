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

package com.duckduckgo.autoconsent.impl

import com.duckduckgo.autoconsent.api.AutoconsentFeatureName
import com.duckduckgo.autoconsent.store.AutoconsentExceptionEntity
import com.duckduckgo.autoconsent.store.AutoconsentFeatureToggleRepository
import com.duckduckgo.autoconsent.store.AutoconsentFeatureToggles
import com.duckduckgo.autoconsent.store.AutoconsentRepository
import com.duckduckgo.autoconsent.store.DisabledCmpsEntity
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AutoconsentFeaturePlugin @Inject constructor(
    private val autoconsentRepository: AutoconsentRepository,
    private val autoconsentFeatureToggleRepository: AutoconsentFeatureToggleRepository,
) : PrivacyFeaturePlugin {

    override fun store(featureName: String, jsonString: String): Boolean {
        val autoconsentFeatureName = autoconsentFeatureValueOf(featureName) ?: return false
        if (autoconsentFeatureName.value == this.featureName) {
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<AutoconsentFeature> =
                moshi.adapter(AutoconsentFeature::class.java)

            val autoconsentFeature: AutoconsentFeature? = jsonAdapter.fromJson(jsonString)

            val disabledCmps = autoconsentFeature?.settings?.disabledCMPs?.map {
                DisabledCmpsEntity(it)
            }.orEmpty()

            val exceptions = autoconsentFeature?.exceptions?.map {
                AutoconsentExceptionEntity(domain = it.domain, reason = it.reason.orEmpty())
            }.orEmpty()

            autoconsentRepository.updateAll(exceptions, disabledCmps)
            val isEnabled = autoconsentFeature?.state == "enabled"
            autoconsentFeatureToggleRepository.insert(
                AutoconsentFeatureToggles(autoconsentFeatureName, isEnabled, autoconsentFeature?.minSupportedVersion),
            )
            return true
        }
        return false
    }

    override val featureName: String = AutoconsentFeatureName.Autoconsent.value
}
