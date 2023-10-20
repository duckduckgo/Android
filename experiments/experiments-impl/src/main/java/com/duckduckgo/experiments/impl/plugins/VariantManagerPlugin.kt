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

package com.duckduckgo.experiments.impl.plugins

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.PrivacyVariantManagerPlugin
import com.duckduckgo.experiments.api.VariantManager
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class VariantManagerPlugin @Inject constructor(
    private val variantManager: VariantManager,
) : PrivacyVariantManagerPlugin {

    override fun store(jsonString: String): Boolean {
        val moshi = Moshi.Builder().build()
        val jsonAdapter: JsonAdapter<VariantManagerConfig> =
            moshi.adapter(VariantManagerConfig::class.java)

        val variants = mutableListOf<com.duckduckgo.experiments.store.ExperimentVariantEntity>()

        val variantManagerConfig: VariantManagerConfig? = jsonAdapter.fromJson(jsonString)
        if (variantManagerConfig?.variants.isNullOrEmpty()) {
            return false
        }
        variantManagerConfig?.variants?.map {
            variants.add(
                com.duckduckgo.experiments.store.ExperimentVariantEntity(
                    it.variantKey,
                    it.desc,
                    it.weight,
                    com.duckduckgo.experiments.store.VariantFiltersEntity(it.filters?.locale.orEmpty()),
                ),
            )
        }
        variantManager.updateVariants() // fixme Noelia

        return true
    }
}
