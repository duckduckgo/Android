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

package com.duckduckgo.privacy.config.impl

import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import logcat.LogPriority.ERROR
import logcat.logcat

internal class VariantManagerPlugin constructor(
    private val variantManager: VariantManager,
) : PrivacyFeaturePlugin {

    override fun store(
        featureName: String,
        jsonString: String,
    ): Boolean {
        val moshi = Moshi.Builder().build()
        val jsonAdapter: JsonAdapter<VariantManagerConfig> =
            moshi.adapter(VariantManagerConfig::class.java)

        val variantManagerConfig: VariantManagerConfig? = runCatching {
            jsonAdapter.fromJson(jsonString)
        }.onFailure {
            logcat(ERROR) {
                """
                    Error: ${it.message}
                    Parsing jsonString: $jsonString
                """.trimIndent()
            }
        }.getOrThrow()

        return variantManagerConfig?.variants?.takeIf { it.isNotEmpty() }?.let { variants ->
            variantManager.updateVariants(variants)
            true
        } ?: false
    }

    override val featureName = VARIANT_MANAGER_FEATURE_NAME

    companion object {
        const val VARIANT_MANAGER_FEATURE_NAME = "experimentalVariants"
    }
}

internal data class VariantManagerConfig(
    val variants: List<VariantConfig>,
)
