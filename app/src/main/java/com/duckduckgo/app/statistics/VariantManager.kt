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
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import timber.log.Timber

@WorkerThread
interface VariantManager {

    sealed class VariantFeature {
    }

    companion object {

        // this will be returned when there are no other active experiments
        val DEFAULT_VARIANT = Variant(key = "", features = emptyList())

        val ACTIVE_VARIANTS = listOf(

            // Shared control. You can use this as your control unless you are experimenting on
            // a subgroup e.g a device API or specific language
            Variant(key = "sc", weight = 1.0, features = emptyList())
        )
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
            val newVariant = generateVariant(activeVariants)
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
    val features: List<VariantManager.VariantFeature> = emptyList()
) : Probabilistic {

    fun hasFeature(feature: VariantManager.VariantFeature) = features.contains(feature)
}
