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
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.BlockingTrackersAcrossWebRemoteMessage
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.DaxDialogMessage
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NextLevelPrivacyNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NextLevelPrivacyRemoteMessage
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NotificationSchedulingBugFix
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.OneEasyStepForPrivacyNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.OneEasyStepForPrivacyRemoteMessage
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import java.util.*
import timber.log.Timber

@WorkerThread
interface VariantManager {

    // variant-dependant features listed here
    sealed class VariantFeature {
        object OneEasyStepForPrivacyRemoteMessage : VariantFeature()
        object BlockingTrackersAcrossWebRemoteMessage : VariantFeature()
        object NextLevelPrivacyRemoteMessage : VariantFeature()
        object OneEasyStepForPrivacyNotification : VariantFeature()
        object NextLevelPrivacyNotification : VariantFeature()
        object DaxDialogMessage : VariantFeature()
        object NotificationSchedulingBugFix : VariantFeature()
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

            // Experiment: Increase retention through AppTP promotions
            Variant(key = "ze", weight = 0.0, features = emptyList(), filterBy = { noFilter() }),
            Variant(key = "zh", weight = 0.0, features = listOf(OneEasyStepForPrivacyRemoteMessage), filterBy = { noFilter() }),
            Variant(key = "zi", weight = 0.0, features = listOf(BlockingTrackersAcrossWebRemoteMessage), filterBy = { noFilter() }),
            Variant(key = "zl", weight = 0.0, features = listOf(NextLevelPrivacyRemoteMessage), filterBy = { noFilter() }),
            Variant(key = "zm", weight = 0.0, features = listOf(OneEasyStepForPrivacyNotification), filterBy = { noFilter() }),
            Variant(key = "zn", weight = 0.0, features = listOf(NextLevelPrivacyNotification), filterBy = { noFilter() }),
            Variant(key = "zo", weight = 0.0, features = listOf(DaxDialogMessage), filterBy = { noFilter() }),

            // Experiment: Increase retention through push notification bug fix
            Variant(key = "zp", weight = 1.0, features = emptyList(), filterBy = { noFilter() }),
            Variant(key = "zq", weight = 1.0, features = listOf(NotificationSchedulingBugFix), filterBy = { noFilter() }),
        )

        val REFERRER_VARIANTS = listOf(
            Variant(RESERVED_EU_AUCTION_VARIANT, features = emptyList(), filterBy = { noFilter() }),
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
    private val indexRandomizer: IndexRandomizer,
    private val appBuildConfig: AppBuildConfig,
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
}

fun VariantManager.isOneEasyStepForPrivacyRemoteMessageEnabled() = this.getVariant().hasFeature(OneEasyStepForPrivacyRemoteMessage)
fun VariantManager.isBlockingTrackersAcrossWebRemoteMessageEnabled() = this.getVariant().hasFeature(BlockingTrackersAcrossWebRemoteMessage)
fun VariantManager.isNextLevelPrivacyRemoteMessageEnabled() = this.getVariant().hasFeature(NextLevelPrivacyRemoteMessage)
fun VariantManager.isOneEasyStepForPrivacyNotificationEnabled() = this.getVariant().hasFeature(OneEasyStepForPrivacyNotification)
fun VariantManager.isNextLevelPrivacyNotificationEnabled() = this.getVariant().hasFeature(NextLevelPrivacyNotification)
fun VariantManager.isDaxDialogMessageEnabled() = this.getVariant().hasFeature(DaxDialogMessage)
fun VariantManager.isNotificationSchedulingBugFixEnabled() = this.getVariant().hasFeature(NotificationSchedulingBugFix)

/**
 * A variant which can be used for experimentation.
 * @param weight Relative weight. These are normalised to all other variants, so they don't have to add up to any specific number.
 *
 */
data class Variant(
    val key: String,
    override val weight: Double = 0.0,
    val features: List<VariantManager.VariantFeature> = emptyList(),
    val filterBy: (config: AppBuildConfig) -> Boolean,
) : Probabilistic {

    fun hasFeature(feature: VariantManager.VariantFeature) = features.contains(feature)
}
