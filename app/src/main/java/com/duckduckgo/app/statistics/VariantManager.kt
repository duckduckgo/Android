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
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import timber.log.Timber
import java.util.*

@WorkerThread
interface VariantManager {

    sealed class VariantFeature {
        object AddWidgetCta : VariantFeature()
        object NotificationDayOne : VariantFeature()
        object NotificationDayThree : VariantFeature()
    }

    companion object {

        // this will be returned when there are no other active experiments
        val DEFAULT_VARIANT = Variant(key = "", features = emptyList())

        val ACTIVE_VARIANTS = listOf(
            // SERP variants - do not remove
            Variant(key = "sa", weight = 1.0, features = emptyList()),
            Variant(key = "sb", weight = 1.0, features = emptyList()),

            // Notifications english speakers
            Variant(key = "mc", weight = 1.0, features = emptyList()),
            Variant(key = "me", weight = 1.0, features = listOf(VariantFeature.NotificationDayOne)),
            Variant(key = "mt", weight = 1.0, features = listOf(VariantFeature.NotificationDayThree)),

            // Notifications non-english speakers
            Variant(key = "md", weight = 1.0, features = emptyList()),
            Variant(key = "mf", weight = 1.0, features = listOf(VariantFeature.NotificationDayOne)),
            Variant(key = "mu", weight = 1.0, features = listOf(VariantFeature.NotificationDayThree)),

            // Add Widget
            Variant(key = "mn", weight = 1.0, features = emptyList()), //control
            Variant(key = "mo", weight = 1.0, features = listOf(VariantFeature.AddWidgetCta))
        )
    }

    fun getVariant(activeVariants: List<Variant> = ACTIVE_VARIANTS): Variant
}

class ExperimentationVariantManager(
    private val store: StatisticsDataStore,
    private val widgetCapabilities: WidgetCapabilities,
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
        val variant = activeVariants[randomizedIndex]

        if ((variant.key == "mn" || variant.key == "mo") && !widgetCapabilities.supportsStandardWidgetAdd) {
            Timber.i("This device does not support $variant.key., using default")
            return DEFAULT_VARIANT
        }

        return adjustNotificationVariantsForLanguage(variant, activeVariants)
    }

    private fun adjustNotificationVariantsForLanguage(variant: Variant, activeVariants: List<Variant>): Variant {
        return when {
            !isLanguageEnglish && variant.key == "mc" -> activeVariants.find { it.key == "md" }!!
            !isLanguageEnglish && variant.key == "me" -> activeVariants.find { it.key == "mf" }!!
            !isLanguageEnglish && variant.key == "mt" -> activeVariants.find { it.key == "mu" }!!
            isLanguageEnglish && variant.key == "md" -> activeVariants.find { it.key == "mc" }!!
            isLanguageEnglish && variant.key == "mf" -> activeVariants.find { it.key == "me" }!!
            isLanguageEnglish && variant.key == "mu" -> activeVariants.find { it.key == "mt" }!!
            else -> variant
        }
    }
}

private val isLanguageEnglish
    get() = Locale.getDefault().isO3Language == Locale.ENGLISH.isO3Language


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
