/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.privacypass.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class PrivacyPassFeaturePlugin @Inject constructor(
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository,
) : PrivacyFeaturePlugin {

    override fun store(
        featureName: String,
        jsonString: String,
    ): Boolean {
        if (featureName != this.featureName) return false

        val parsedFeature = parseFeature(jsonString) ?: return false
        val isEnabled = parsedFeature.state == "enabled"

        privacyFeatureTogglesRepository.insert(
            PrivacyFeatureToggles(
                featureName = this.featureName,
                isEnabled = isEnabled,
                minSupportedVersion = parsedFeature.minSupportedVersion,
            ),
        )
        return true
    }

    override val featureName: String = PrivacyFeatureName.PrivacyPassFeatureName.value

    private fun parseFeature(jsonString: String): PrivacyPassFeature? {
        return try {
            val jsonObject = JSONObject(jsonString)
            val minSupportedVersion = if (jsonObject.has("minSupportedVersion") && !jsonObject.isNull("minSupportedVersion")) {
                jsonObject.getInt("minSupportedVersion")
            } else {
                null
            }
            PrivacyPassFeature(
                state = jsonObject.optString("state").takeIf { it.isNotBlank() },
                minSupportedVersion = minSupportedVersion,
            )
        } catch (_: JSONException) {
            null
        }
    }
}

private data class PrivacyPassFeature(
    val state: String?,
    val minSupportedVersion: Int?,
)
