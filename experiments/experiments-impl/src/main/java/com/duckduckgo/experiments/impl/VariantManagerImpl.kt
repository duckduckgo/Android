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
        val activeVariants = convertEntitiesToVariants(experimentVariantRepository.getActiveVariants())
        val currentVariantKey = store.variant

        if (!experimentVariantRepository.isVariantManagerConfigReady() || currentVariantKey == DEFAULT_VARIANT.key) {
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
                    localeFilter = it.filters?.locale.orEmpty(),
                ),
            )
        }
        experimentVariantRepository.updateVariants(variantEntityList)
    }

    override fun variantConfigDownloaded() {
        experimentVariantRepository.variantConfigDownloaded()
    }

    private fun convertEntitiesToVariants(activeVariantEntities: List<ExperimentVariantEntity>): List<Variant> {
        val activeVariants: MutableList<Variant> = mutableListOf()
        activeVariantEntities.map { entity ->
            activeVariants.add(
                Variant(
                    key = entity.key,
                    weight = entity.weight ?: 0.0,
                    filterBy = addFilters(entity),
                ),
            )
        }
        return activeVariants
    }

    private fun addFilters(entity: ExperimentVariantEntity): (AppBuildConfig) -> Boolean {
        if (entity.key == "sc" || entity.key == "se") {
            return { isSerpRegionToggleCountry() }
        }
        if (entity.localeFilter.isEmpty()) {
            return { noFilter() }
        }

        val userLocale = Locale.getDefault()
        return { entity.localeFilter.contains(userLocale.toString()) }
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

    private fun persistVariant(newVariant: Variant) {
        store.variant = newVariant.key
    }

    companion object {

        private const val RESERVED_EU_AUCTION_VARIANT = "ml"

        // this will be returned when there are no other active experiments
        private val DEFAULT_VARIANT = Variant(key = "", filterBy = { noFilter() })

        private val REFERRER_VARIANTS = listOf(
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

        private fun referrerVariant(key: String): Variant {
            val knownReferrer = REFERRER_VARIANTS.firstOrNull { it.key == key }
            return knownReferrer ?: Variant(key, filterBy = { noFilter() })
        }

        private fun noFilter(): Boolean = true

        private fun isSerpRegionToggleCountry(): Boolean {
            val locale = Locale.getDefault()
            return serpRegionToggleTargetCountries.contains(locale.country)
        }
    }
}
