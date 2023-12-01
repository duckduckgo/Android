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
import com.duckduckgo.app.statistics.ReinstallAtbListener.Companion.REINSTALL_USER_VARIANT
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
    private val indexRandomizer: IndexRandomizer,
    private val appBuildConfig: AppBuildConfig,
    private val experimentVariantRepository: ExperimentVariantRepository,
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

    override fun saveVariants(variants: List<VariantConfig>) {
        experimentVariantRepository.saveVariants(variants)
        Timber.d("Variants update ${experimentVariantRepository.getActiveVariants()}")

        val activeVariants = convertEntitiesToVariants(experimentVariantRepository.getActiveVariants())
        val currentVariantKey = experimentVariantRepository.getUserVariant()

        updateUserVariant(activeVariants, currentVariantKey)
    }

    private fun updateUserVariant(activeVariants: List<Variant>, currentVariantKey: String?) {
        if (currentVariantKey == DEFAULT_VARIANT.key) {
            return
        }

        if (currentVariantKey == REINSTALL_USER_VARIANT) {
            return
        }

        if (currentVariantKey != null && matchesReferrerVariant(currentVariantKey)) {
            return
        }

        if (currentVariantKey == null) {
            allocateNewVariant(activeVariants)
            return
        }

        val keyInActiveVariants = activeVariants.map { it.key }.contains(currentVariantKey)
        if (!keyInActiveVariants) {
            Timber.i("Variant $currentVariantKey no longer an active variant; user will now use default variant")
            val newVariant = DEFAULT_VARIANT
            experimentVariantRepository.updateVariant(newVariant.key)
            return
        }

        Timber.i("Variant $currentVariantKey is still in use, no need to update")
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

    private fun matchesReferrerVariant(key: String): Boolean {
        return key == experimentVariantRepository.getAppReferrerVariant()
    }

    private fun allocateNewVariant(activeVariants: List<Variant>): Variant {
        var newVariant = generateVariant(activeVariants)
        val compliesWithFilters = newVariant.filterBy(appBuildConfig)

        if (!compliesWithFilters || appBuildConfig.isDefaultVariantForced) {
            newVariant = DEFAULT_VARIANT
        }
        Timber.i("Current variant is null; allocating new one $newVariant")
        experimentVariantRepository.updateVariant(newVariant.key)
        return newVariant
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
        private val DEFAULT_VARIANT = Variant(key = "", filterBy = { noFilter() })

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

        private fun noFilter(): Boolean = true

        private fun isSerpRegionToggleCountry(): Boolean {
            val locale = Locale.getDefault()
            return serpRegionToggleTargetCountries.contains(locale.country)
        }
    }
}
