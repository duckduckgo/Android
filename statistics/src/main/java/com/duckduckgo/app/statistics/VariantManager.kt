/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.statistics

import androidx.annotation.WorkerThread
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.duckduckgo.app.statistics.VariantManager.Companion.referrerVariant
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import timber.log.Timber
import java.util.*

@WorkerThread
interface VariantManager {

    // variant-dependant features listed here
    sealed class VariantFeature {
        object FavoritesOnboarding : VariantFeature()
    }

    companion object {

        const val RESERVED_EU_AUCTION_VARIANT = "ml"

        // this will be returned when there are no other active experiments
        val DEFAULT_VARIANT = Variant(key = "", features = emptyList(), filterBy = { noFilter() })

        val ACTIVE_VARIANTS = listOf(
            // SERP variants. "sc" may also be used as a shared control for mobile experiments in
            // the future if we can filter by app version
            Variant(key = "sc", weight = 0.0, features = emptyList(), filterBy = { isSerpRegionToggleCountry() }),
            Variant(key = "se", weight = 0.0, features = emptyList(), filterBy = { isSerpRegionToggleCountry() }),
            Variant(key = "gx", weight = 1.0, features = emptyList(), filterBy = { noFilter() }),
            Variant(key = "gy", weight = 1.0, features = emptyList(), filterBy = { noFilter() }),
            // Favorites onboarding
            Variant(key = "pz", weight = 1.0, features = emptyList(), filterBy = { isEnglishLocale() }),
            Variant(key = "po", weight = 1.0, features = listOf(VariantFeature.FavoritesOnboarding), filterBy = { isEnglishLocale() }),
        )

        val REFERRER_VARIANTS = listOf(
            Variant(RESERVED_EU_AUCTION_VARIANT, features = emptyList(), filterBy = { noFilter() })
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
            "GB"
        )

        fun referrerVariant(key: String): Variant {
            val knownReferrer = REFERRER_VARIANTS.firstOrNull { it.key == key }
            return knownReferrer ?: Variant(key, features = emptyList(), filterBy = { noFilter() })
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

    fun getVariant(activeVariants: List<Variant> = ACTIVE_VARIANTS): Variant

    fun updateAppReferrerVariant(variant: String)
}

class ExperimentationVariantManager(
    private val store: StatisticsDataStore,
    private val indexRandomizer: IndexRandomizer
) : VariantManager {

    @Synchronized
    override fun getVariant(activeVariants: List<Variant>): Variant {
        val currentVariantKey = store.variant

        if (currentVariantKey == DEFAULT_VARIANT.key) {
            return DEFAULT_VARIANT
        }

        if (currentVariantKey != null && matchesReferrerVariant(currentVariantKey)) {
            return referrerVariant(currentVariantKey)
        }

        if (currentVariantKey == null || activeVariants.isEmpty()) {
            return allocateNewVariant(activeVariants)
        }

        val currentVariant = lookupVariant(currentVariantKey, activeVariants)
        if (currentVariant == null) {
            Timber.i("Variant $currentVariantKey no longer an active variant; user will now use default variant")
            val newVariant = DEFAULT_VARIANT
            persistVariant(newVariant)
            return newVariant
        }

        return currentVariant
    }

    private fun allocateNewVariant(activeVariants: List<Variant>): Variant {
        var newVariant = generateVariant(activeVariants)
        val compliesWithFilters = newVariant.filterBy()

        if (!compliesWithFilters) {
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

    private fun lookupVariant(key: String?, activeVariants: List<Variant>): Variant? {
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
}

/**
 * A variant which can be used for experimentation.
 * @param weight Relative weight. These are normalised to all other variants, so they don't have to add up to any specific number.
 *
 */
data class Variant(
    val key: String,
    override val weight: Double = 0.0,
    val features: List<VariantManager.VariantFeature> = emptyList(),
    val filterBy: () -> Boolean
) : Probabilistic {

    fun hasFeature(feature: VariantManager.VariantFeature) = features.contains(feature)
}
