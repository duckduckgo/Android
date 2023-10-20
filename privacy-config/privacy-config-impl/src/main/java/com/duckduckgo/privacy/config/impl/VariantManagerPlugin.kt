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

import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Qualifier

internal class VariantManagerPlugin constructor(
    private val variantManager: VariantManager,
) : PrivacyFeaturePlugin {

    override fun store(
        featureName: String,
        jsonString: String
    ): Boolean {
        val moshi = Moshi.Builder().build()
        val jsonAdapter: JsonAdapter<VariantManagerConfig> =
            moshi.adapter(VariantManagerConfig::class.java)

        val variantManagerConfig: VariantManagerConfig? = runCatching {
            jsonAdapter.fromJson(jsonString)
        }.onFailure {
            Timber.e(
                """
                    Error: ${it.message}
                    Parsing jsonString: $jsonString
                """.trimIndent()
            )
        }.getOrThrow()
        if (variantManagerConfig?.variants.isNullOrEmpty()) {
            return false
        }

        variantManager.updateVariants() // fixme Noelia

        return true
    }

    override val featureName = "variantManager"
}


internal data class VariantManagerConfig(
    val variants: List<VariantConfig>,
)

internal data class VariantConfig(
    val variantKey: String,
    val weight: Float? = 0.0f,
    val filters: VariantFilters? = null,
)

internal data class VariantFilters(
    val locale: List<String>? = null,
)

