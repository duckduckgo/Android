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

package com.duckduckgo.app.statistics.variantmanager

import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.PrivacyVariantManagerPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class VariantManagerPlugin @Inject constructor(
    private val VariantManager: VariantManager,
) : PrivacyVariantManagerPlugin {

    override fun store(jsonString: String): Boolean {
        val moshi = Moshi.Builder().build()
        val jsonAdapter: JsonAdapter<VariantManagerConfig> =
            moshi.adapter(VariantManagerConfig::class.java)

        val variants = mutableListOf<ExperimentVariantEntity>()

        val variantManagerConfig: VariantManagerConfig? = jsonAdapter.fromJson(jsonString)
        variantManagerConfig?.variants?.map {
            variants.add(
                ExperimentVariantEntity(
                    it.variantKey,
                    it.desc,
                    it.weight,
                    VariantFiltersEntity(it.filters?.locale),
                ),
            )

            return true
        }
        return false
    }
}
