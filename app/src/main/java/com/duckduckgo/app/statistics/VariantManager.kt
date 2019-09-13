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

import android.os.Build
import androidx.annotation.WorkerThread
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import timber.log.Timber
import java.util.Locale

@WorkerThread
interface VariantManager {

    sealed class VariantFeature {
        // variant-dependant features listed here
        object TabSwitcherGrid : VariantFeature()

        object OnboardingExperiment : VariantFeature()
    }

    companion object {

        // this will be returned when there are no other active experiments
        val DEFAULT_VARIANT = Variant(key = "", features = emptyList(), filterBy = { noFilter() })

        val ACTIVE_VARIANTS = listOf(

            // SERP variants. "sc" may also be used as a shared control for mobile experiments in
            // the future if we can filter by app version
            Variant(key = "sc", weight = 0.0, features = emptyList(), filterBy = { noFilter() }),
            Variant(key = "se", weight = 0.0, features = emptyList(), filterBy = { noFilter() }),

            // All groups in an experiment (control and variants) MUST use the same filters
            Variant(key = "mw", weight = 1.0, features = emptyList(), filterBy = { noFilter() }),
            Variant(key = "mx", weight = 1.0, features = listOf(VariantFeature.TabSwitcherGrid), filterBy = { noFilter() }),

            Variant(key = "mp", weight = 1.0, features = emptyList(), filterBy = { isEnglishLocale() && apiIsNougatOrAbove() && isNotHuawei() }),
            Variant(
                key = "mo",
                weight = 1.0,
                features = listOf(VariantFeature.OnboardingExperiment),
                filterBy = { isEnglishLocale() && apiIsNougatOrAbove() && isNotHuawei() })
        )

        private fun isNotHuawei() = Build.MANUFACTURER != "HUAWEI"

        private fun apiIsNougatOrAbove(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

        private fun noFilter(): Boolean = true

        private fun isEnglishLocale(): Boolean {
            val locale = Locale.getDefault()
            return locale != null && locale.language == "en"
        }
    }

    fun getVariant(activeVariants: List<Variant> = ACTIVE_VARIANTS): Variant
}

class ExperimentationVariantManager(
    private val store: StatisticsDataStore,
    private val indexRandomizer: IndexRandomizer
) : VariantManager {

    @Synchronized
    override fun getVariant(activeVariants: List<Variant>): Variant {
        if (activeVariants.isEmpty()) return DEFAULT_VARIANT

        val currentVariantKey = store.variant

        if (currentVariantKey == DEFAULT_VARIANT.key) {
            return DEFAULT_VARIANT
        }

        if (currentVariantKey == null) {
            var newVariant = generateVariant(activeVariants)
            val compliesWithFilters = newVariant.filterBy()

            if (!compliesWithFilters) {
                newVariant = DEFAULT_VARIANT
            }
            Timber.i("Current variant is null; allocating new one $newVariant")
            persistVariant(newVariant)
            return newVariant
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

    private fun lookupVariant(key: String?, activeVariants: List<Variant>): Variant? =
        activeVariants.firstOrNull { it.key == key }

    private fun persistVariant(newVariant: Variant) {
        store.variant = newVariant.key
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
