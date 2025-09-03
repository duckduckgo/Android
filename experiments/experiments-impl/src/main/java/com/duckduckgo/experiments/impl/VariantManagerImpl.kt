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

package com.duckduckgo.experiments.impl

import androidx.annotation.WorkerThread
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.experiments.impl.reinstalls.REINSTALL_VARIANT
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat

@WorkerThread
@ContributesBinding(AppScope::class)
class VariantManagerImpl @Inject constructor(
    private val indexRandomizer: IndexRandomizer,
    private val appBuildConfig: AppBuildConfig,
    private val experimentVariantRepository: ExperimentVariantRepository,
    private val experimentFiltersManager: ExperimentFiltersManager,
) : VariantManager {

    override fun defaultVariantKey(): String {
        return DEFAULT_VARIANT.key
    }

    override fun getVariantKey(): String? {
        return experimentVariantRepository.getUserVariant()
    }

    override fun updateAppReferrerVariant(variant: String) {
        experimentVariantRepository.updateAppReferrerVariant(variant)
    }

    override fun updateVariants(variants: List<VariantConfig>) {
        val activeVariants = variants.toVariants()
        logcat { "Variants update $variants" }
        val currentVariantKey = experimentVariantRepository.getUserVariant()

        updateUserVariant(activeVariants, currentVariantKey)
    }

    private fun updateUserVariant(activeVariants: List<Variant>, currentVariantKey: String?) {
        if (currentVariantKey == DEFAULT_VARIANT.key) {
            return
        }

        if (currentVariantKey != null && matchesReferrerVariant(currentVariantKey)) {
            return
        }

        if (currentVariantKey == REINSTALL_VARIANT) {
            return
        }

        if (currentVariantKey == null) {
            allocateNewVariant(activeVariants)
            return
        }

        val keyInActiveVariants = activeVariants.map { it.key }.contains(currentVariantKey)
        if (!keyInActiveVariants) {
            logcat(INFO) { "Variant $currentVariantKey no longer an active variant; user will now use default variant" }
            val newVariant = DEFAULT_VARIANT
            experimentVariantRepository.updateVariant(newVariant.key)
            return
        }

        logcat(INFO) { "Variant $currentVariantKey is still in use; no need to update" }
    }

    private fun List<VariantConfig>.toVariants(): List<Variant> {
        val activeVariants: MutableList<Variant> = mutableListOf()
        this.map { entity ->
            activeVariants.add(
                Variant(
                    key = entity.variantKey,
                    weight = entity.weight ?: 0.0,
                    filterBy = experimentFiltersManager.computeFilters(entity),
                ),
            )
        }
        return activeVariants
    }

    private fun matchesReferrerVariant(key: String): Boolean {
        return key == experimentVariantRepository.getAppReferrerVariant()
    }

    private fun allocateNewVariant(activeVariants: List<Variant>): Variant {
        var newVariant = generateVariant(activeVariants)
        val compliesWithFilters = newVariant.filterBy(appBuildConfig)

        if (!compliesWithFilters || appBuildConfig.isDefaultVariantForced) {
            newVariant = DEFAULT_VARIANT
        }
        logcat(INFO) { "Current variant is null; allocating new one ${newVariant.key}" }
        experimentVariantRepository.updateVariant(newVariant.key)
        return newVariant
    }

    private fun generateVariant(activeVariants: List<Variant>): Variant {
        val weightSum = activeVariants.sumByDouble { it.weight }
        if (weightSum == 0.0) {
            logcat(VERBOSE) { "No variants active; allocating default" }
            return DEFAULT_VARIANT
        }
        val randomizedIndex = indexRandomizer.random(activeVariants)
        return activeVariants[randomizedIndex]
    }

    companion object {

        /**
         * Since March 7th 2024 there are two choice screens on Android
         * Once for Search choice (existing since 2021) and Browser Choice (new since 2024)
         * We want to be able to measure installs and retention from both screens separately
         * https://app.asana.com/0/0/1206729008769473/f
         */
        const val RESERVED_EU_SEARCH_CHOICE_AUCTION_VARIANT = "ml"
        const val RESERVED_EU_BROWSER_CHOICE_AUCTION_VARIANT = "mm"

        // this will be returned when there are no other active experiments
        val DEFAULT_VARIANT = Variant(key = "", filterBy = { noFilter() })

        private fun noFilter(): Boolean = true
    }
}
