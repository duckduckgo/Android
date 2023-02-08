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

package com.duckduckgo.autofill.impl.feature.settings

import com.duckduckgo.autofill.api.feature.AutofillSubfeature
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName.InjectCredentials
import com.duckduckgo.autofill.impl.autofillSubFeatureValueOf
import com.duckduckgo.autofill.impl.feature.plugin.AutofillSubFeaturePlugin
import com.duckduckgo.autofill.store.feature.AutofillFeatureToggleRepository
import com.duckduckgo.autofill.store.feature.AutofillSubfeatureToggle
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import timber.log.Timber

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AutofillSubFeaturePlugin::class,
)
class AutofillSubFeatureConfigProcessorInjectCredentials @Inject constructor(
    private val repository: AutofillFeatureToggleRepository,
    moshi: Moshi,
) : AutofillSubFeaturePlugin {

    private data class JsonConfigModel(
        val state: String?,
        val minSupportedVersion: Int?,
    )

    override val settingName: AutofillSubfeature = InjectCredentials
    private val jsonAdapter = moshi.adapter(JsonConfigModel::class.java)

    override fun store(rawJson: String): Boolean {
        val subFeatureElement = autofillSubFeatureValueOf(settingName.value) ?: return false
        Timber.v("Received autofill subfeature configuration: %s", rawJson)
        parseSubFeature(rawJson)?.let { jsonConfig ->

            repository.insert(
                AutofillSubfeatureToggle(
                    featureName = subFeatureElement,
                    enabled = jsonConfig.state == "enabled",
                    minSupportedVersion = jsonConfig.minSupportedVersion,
                ),
            )

            return true
        }
        return false
    }

    private fun parseSubFeature(rawJson: String): JsonConfigModel? {
        runCatching {
            return jsonAdapter.fromJson(rawJson)
        }.onFailure {
            Timber.w("Failed to parse subfeature [%s]: %s", settingName.value, it.message)
        }
        return null
    }
}
