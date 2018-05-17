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

import android.support.annotation.WorkerThread
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.DefaultBrowserFeature.ShowInOnboarding
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.DefaultBrowserFeature.ShowTimedReminder
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import timber.log.Timber

@WorkerThread
interface VariantManager {

    sealed class VariantFeature {

        sealed class DefaultBrowserFeature : VariantFeature() {
            object ShowInOnboarding : DefaultBrowserFeature()
            object ShowTimedReminder : DefaultBrowserFeature()
        }
    }

    companion object {

        // there must always be at least one active variant defined here
        val ACTIVE_VARIANTS = listOf(
            Variant(key = "mw", weight = 25.0, features = listOf(ShowInOnboarding)),
            Variant(key = "mx", weight = 25.0, features = listOf(ShowInOnboarding, ShowTimedReminder)),
            Variant(key = "my", weight = 50.0, features = emptyList())
        )
    }

    fun getVariant(activeVariants: List<Variant> = ACTIVE_VARIANTS): Variant
}

class ExperimentationVariantManager(
    private val store: StatisticsDataStore,
    private val indexRandomizer: IndexRandomizer
) : VariantManager {

    override fun getVariant(activeVariants: List<Variant>): Variant {
        if (activeVariants.isEmpty()) throw IllegalArgumentException("There needs to be at least one active variant")

        val currentVariantKey = store.variant
        val currentVariant = lookupVariant(currentVariantKey, activeVariants)

        return if (currentVariant == null) {
            val newVariant = generateVariant(activeVariants)
            Timber.i("Current variant is null; allocating new one $newVariant")
            persistVariant(newVariant)
            newVariant
        } else {
            currentVariant
        }
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
    override val weight: Double,
    val features: List<VariantManager.VariantFeature> = emptyList()
) : Probabilistic {

    fun hasFeature(feature: VariantManager.VariantFeature) = features.contains(feature)
}
