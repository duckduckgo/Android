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

package com.duckduckgo.autofill.impl.feature.plugin

import com.duckduckgo.autofill.api.feature.AutofillFeatureName
import com.duckduckgo.autofill.api.feature.AutofillSubfeature
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName
import com.duckduckgo.autofill.store.AutofillExceptionEntity
import com.duckduckgo.autofill.store.feature.AutofillFeatureRepository
import com.duckduckgo.autofill.store.feature.AutofillFeatureToggleRepository
import com.duckduckgo.autofill.store.feature.AutofillFeatureToggles
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import org.json.JSONObject
import timber.log.Timber

@ContributesMultibinding(AppScope::class)
class AutofillFeaturePlugin @Inject constructor(
    private val autofillFeatureRepository: AutofillFeatureRepository,
    private val autofillFeatureToggleRepository: AutofillFeatureToggleRepository,
    private val autofillSubfeatureJsonParser: AutofillSubfeatureJsonParser,
    private val moshi: Moshi,
) : PrivacyFeaturePlugin {
    override val featureName: String = AutofillFeatureName.Autofill.value

    override fun store(
        featureName: String,
        jsonString: String,
    ): Boolean {
        val element = getAutofillElement(featureName) ?: return false

        if (element.value == this.featureName) {
            val feature = parseJson(jsonString) ?: return false

            val exceptions = parseExceptions(feature)
            autofillFeatureRepository.updateAllExceptions(exceptions)

            val isEnabled = feature.state == "enabled"
            autofillFeatureToggleRepository.insert(AutofillFeatureToggles(element, isEnabled, feature.minSupportedVersion))

            feature.settings?.forEach { setting ->
                Timber.v("Processing autofill setting: %s", setting.key)

                when (setting.key) {
                    "features" -> {
                        setting.value?.let { subfeatureJson ->
                            autofillSubfeatureJsonParser.processSubfeatures(subfeatureJson)
                        }
                    }

                    else -> Timber.w("Ignoring %s autofill setting", setting.key)
                }
            }

            return true
        }
        return false
    }

    private fun parseJson(jsonString: String): AutofillFeatureJson? {
        val jsonAdapter = moshi.adapter(AutofillFeatureJson::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    private fun parseExceptions(autofillFeature: AutofillFeatureJson?): List<AutofillExceptionEntity> {
        val autofillExceptions = mutableListOf<AutofillExceptionEntity>()
        autofillFeature?.exceptions?.map {
            autofillExceptions.add(AutofillExceptionEntity(it.domain, it.reason))
        }
        return autofillExceptions
    }
}

interface AutofillSubfeaturePlugin {
    fun store(rawJson: String): Boolean

    val settingName: AutofillSubfeature
}

fun getAutofillElement(featureName: String): AutofillFeatureName? {
    return AutofillFeatureName.values().find { it.value == featureName }
}

fun getAutofillSubfeatureElement(featureName: String): AutofillSubfeatureName? {
    return AutofillSubfeatureName.values().find { it.value == featureName }
}

data class AutofillFeatureJson(
    val state: String?,
    val minSupportedVersion: Int?,
    val exceptions: List<AutofillExceptionEntity>,
    val settings: Map<String, JSONObject?>?,
)
