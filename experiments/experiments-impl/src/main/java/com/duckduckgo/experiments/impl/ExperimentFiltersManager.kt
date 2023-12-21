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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.impl.store.ExperimentVariantEntity
import com.squareup.anvil.annotations.ContributesBinding
import java.util.Locale
import javax.inject.Inject

interface ExperimentFiltersManager {
    fun addFilters(entity: ExperimentVariantEntity): (AppBuildConfig) -> Boolean
}

@ContributesBinding(AppScope::class)
class ExperimentFiltersManagerImpl @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : ExperimentFiltersManager {
    override fun addFilters(entity: ExperimentVariantEntity): (AppBuildConfig) -> Boolean {
        if (entity.key == "sc" || entity.key == "se") {
            return { isSerpRegionToggleCountry() }
        }

        var matchesLocaleFilter = true
        var matchesAndroidVersionFilter = true

        if (entity.localeFilter.isNotEmpty()) {
            val userLocale = Locale.getDefault()
            matchesLocaleFilter = entity.localeFilter.contains(userLocale.toString())
        }
        if (entity.androidVersionFilter.isNotEmpty()) {
            val userAndroidVersion = appBuildConfig.sdkInt.toString()
            matchesAndroidVersionFilter = entity.androidVersionFilter.contains(userAndroidVersion)
        }

        return { matchesLocaleFilter && matchesAndroidVersionFilter }
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
}
