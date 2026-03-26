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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.impl.ExperimentFiltersManagerImpl.ExperimentFilterType.ANDROID_VERSION
import com.duckduckgo.experiments.impl.ExperimentFiltersManagerImpl.ExperimentFilterType.LOCALE
import com.duckduckgo.experiments.impl.ExperimentFiltersManagerImpl.ExperimentFilterType.PRIVACY_PRO_ELIGIBLE
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

interface ExperimentFiltersManager {
    /**
     * This method computes the result of the filters
     * @return returns `true` if the filters match the client state, else `false`
     */
    fun computeFilters(entity: VariantConfig): (AppBuildConfig) -> Boolean
}

@ContributesBinding(AppScope::class)
class ExperimentFiltersManagerImpl @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val subscriptions: Subscriptions,
    private val dispatcherProvider: DispatcherProvider,
) : ExperimentFiltersManager {
    override fun computeFilters(entity: VariantConfig): (AppBuildConfig) -> Boolean {
        if (entity.variantKey == "sc" || entity.variantKey == "se") {
            return { isSerpRegionToggleCountry() }
        }

        val filters: MutableMap<ExperimentFilterType, Boolean> = mutableMapOf(
            LOCALE to true,
            ANDROID_VERSION to true,
            PRIVACY_PRO_ELIGIBLE to true,
        )

        if (!entity.filters?.locale.isNullOrEmpty()) {
            val userLocale = appBuildConfig.deviceLocale
            filters[LOCALE] = entity.filters!!.locale.contains(userLocale.toString())
        }
        if (!entity.filters?.androidVersion.isNullOrEmpty()) {
            val userAndroidVersion = appBuildConfig.sdkInt.toString()
            filters[ANDROID_VERSION] = entity.filters!!.androidVersion.contains(userAndroidVersion)
        }
        if (entity.filters?.privacyProEligible != null) {
            val privacyProEligible = runBlocking(dispatcherProvider.io()) { subscriptions.isEligible() }
            filters[PRIVACY_PRO_ELIGIBLE] = entity.filters?.privacyProEligible == privacyProEligible
        }

        return { filters.filter { !it.value }.isEmpty() }
    }

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

    private fun isSerpRegionToggleCountry(): Boolean {
        val locale = Locale.getDefault()
        return serpRegionToggleTargetCountries.contains(locale.country)
    }

    enum class ExperimentFilterType {
        LOCALE,
        ANDROID_VERSION,
        PRIVACY_PRO_ELIGIBLE,
    }
}
