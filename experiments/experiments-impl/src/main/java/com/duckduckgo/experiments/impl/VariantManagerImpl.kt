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
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.experiments.impl.store.ExperimentVariantEntity
import com.duckduckgo.experiments.impl.store.VariantFiltersEntity
import com.squareup.anvil.annotations.ContributesBinding
import java.util.Locale
import javax.inject.Inject
import timber.log.Timber

@WorkerThread
@ContributesBinding(AppScope::class)
class VariantManagerImpl @Inject constructor(
    private val store: StatisticsDataStore,
    private val indexRandomizer: IndexRandomizer,
    private val appBuildConfig: AppBuildConfig,
    private val experimentVariantRepository: ExperimentVariantRepository,
) : VariantManager {

    override fun defaultVariantKey(): String {
        return DEFAULT_VARIANT.key
    }

    @Synchronized
    override fun getVariantKey(): String {
        val activeVariants = ACTIVE_VARIANTS
        val currentVariantKey = store.variant

        if (currentVariantKey == DEFAULT_VARIANT.key) {
            return DEFAULT_VARIANT.key
        }

        if (currentVariantKey != null && matchesReferrerVariant(currentVariantKey)) {
            return referrerVariant(currentVariantKey).key
        }

        if (currentVariantKey == null || activeVariants.isEmpty()) {
            return allocateNewVariant(activeVariants).key
        }

        val currentVariant = lookupVariant(currentVariantKey, activeVariants)
        if (currentVariant == null) {
            Timber.i("Variant $currentVariantKey no longer an active variant; user will now use default variant")
            val newVariant = DEFAULT_VARIANT
            persistVariant(newVariant)
            return newVariant.key
        }

        return currentVariant.key
    }

    private fun allocateNewVariant(activeVariants: List<Variant>): Variant {
        var newVariant = generateVariant(activeVariants)
        val compliesWithFilters = newVariant.filterBy(appBuildConfig)

        if (!compliesWithFilters || appBuildConfig.isDefaultVariantForced) {
            newVariant = DEFAULT_VARIANT
        }
        Timber.i("Current variant is null; allocating new one $newVariant")
        persistVariant(newVariant)
        return newVariant
    }

    override fun updateAppReferrerVariant(variant: String) {
        Timber.i("Updating variant for app referer: $variant")
        store.variant = variant
        store.referrerVariant = variant
    }

    override fun saveVariants(variants: List<VariantConfig>) {
        val variantEntityList: MutableList<ExperimentVariantEntity> = mutableListOf()
        variants.map {
            variantEntityList.add(
                ExperimentVariantEntity(
                    key = it.variantKey,
                    weight = it.weight,
                    filters = VariantFiltersEntity(it.filters?.locale.orEmpty()),
                ),
            )
        }
        experimentVariantRepository.updateVariants(variantEntityList)
    }

    private fun lookupVariant(
        key: String?,
        activeVariants: List<Variant>,
    ): Variant? {
        val variant = activeVariants.firstOrNull { it.key == key }

        if (variant != null) return variant

        if (key != null && matchesReferrerVariant(key)) {
            return referrerVariant(key)
        }

        return null
    }

    private fun persistVariant(newVariant: Variant) {
        store.variant = newVariant.key
    }

    private fun matchesReferrerVariant(key: String): Boolean {
        return key == store.referrerVariant
    }

    private fun generateVariant(activeVariants: List<Variant>): Variant {
        val weightSum = activeVariants.sumByDouble { it.weight }
        if (weightSum == 0.0) {
            Timber.v("No variants active; allocating default")
            return DEFAULT_VARIANT
        }
        val randomizedIndex = indexRandomizer.random(activeVariants)
        return activeVariants[randomizedIndex]
    }

    companion object {

        const val RESERVED_EU_AUCTION_VARIANT = "ml"

        // this will be returned when there are no other active experiments
        val DEFAULT_VARIANT = Variant(key = "", filterBy = { noFilter() })

        val ACTIVE_VARIANTS = listOf(
            // SERP variants. "sc" may also be used as a shared control for mobile experiments in
            // the future if we can filter by app version
            Variant(key = "sc", weight = 0.0, filterBy = { isSerpRegionToggleCountry() }),
            Variant(key = "se", weight = 0.0, filterBy = { isSerpRegionToggleCountry() }),
        )

        val REFERRER_VARIANTS = listOf(
            Variant(RESERVED_EU_AUCTION_VARIANT, filterBy = { noFilter() }),
        )

        private val serpRegionToggleTargetCountries = listOf(
            "AU",
            "AT",
            "DK",
            "FI",
            "FR",
            "DE",
            "IT",
            "IE",
            "NZ",
            "NO",
            "ES",
            "SE",
            "GB",
        )

        fun referrerVariant(key: String): Variant {
            val knownReferrer = REFERRER_VARIANTS.firstOrNull { it.key == key }
            return knownReferrer ?: Variant(key, filterBy = { noFilter() })
        }

        private fun noFilter(): Boolean = true

        private fun isEnglishLocale(): Boolean {
            val locale = Locale.getDefault()
            return locale != null && locale.language == "en"
        }

        private fun isSerpRegionToggleCountry(): Boolean {
            val locale = Locale.getDefault()
            return locale != null && serpRegionToggleTargetCountries.contains(locale.country)
        }
    }
}
