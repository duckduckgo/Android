/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.survey.rmf

import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.survey.ui.SurveyActivity.Companion.SurveySource.IN_APP
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.survey.api.SurveyParameterPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AtbSurveyParameterPlugin @Inject constructor(
    private val statisticsStore: StatisticsDataStore,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "atb"

    override suspend fun evaluate(paramKey: String): String = statisticsStore.atb?.version ?: ""
}

@ContributesMultibinding(AppScope::class)
class AtbVariantSurveyParameterPlugin @Inject constructor(
    private val statisticsStore: StatisticsDataStore,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "var"

    override suspend fun evaluate(paramKey: String): String = statisticsStore.variant ?: ""
}

@ContributesMultibinding(AppScope::class)
class DaysInstalledSurveyParameterPlugin @Inject constructor(
    private val appInstallStore: AppInstallStore,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "delta"

    override suspend fun evaluate(paramKey: String): String = "${appInstallStore.daysInstalled()}"
}

@ContributesMultibinding(AppScope::class)
class AndroidVersionSurveyParameterPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "av"

    override suspend fun evaluate(paramKey: String): String = "${appBuildConfig.sdkInt}"
}

@ContributesMultibinding(AppScope::class)
class AppVersionSurveyParameterPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "ddgv"

    override suspend fun evaluate(paramKey: String): String = appBuildConfig.versionName
}

@ContributesMultibinding(AppScope::class)
class ManufacturerSurveyParameterPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "man"

    override suspend fun evaluate(paramKey: String): String = appBuildConfig.manufacturer
}

@ContributesMultibinding(AppScope::class)
class ModelSurveyParameterPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "mo"

    override suspend fun evaluate(paramKey: String): String = appBuildConfig.model
}

@ContributesMultibinding(AppScope::class)
class SourceSurveyParameterPlugin @Inject constructor() : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "src"

    override suspend fun evaluate(paramKey: String): String = IN_APP.name.lowercase()
}

@ContributesMultibinding(AppScope::class)
class LastActiveDateSurveyParameterPlugin @Inject constructor(
    private val appDaysUsedRepository: AppDaysUsedRepository,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "da"

    override suspend fun evaluate(paramKey: String): String = appDaysUsedRepository.getLastActiveDay()
}

@ContributesMultibinding(AppScope::class)
class LocaleSurveyParameterPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "locale"

    override suspend fun evaluate(paramKey: String): String = "${appBuildConfig.deviceLocale.language}-${appBuildConfig.deviceLocale.country}"
}

@ContributesMultibinding(AppScope::class)
class CohortSurveyParameterPlugin @Inject constructor(
    private val featureTogglesInventory: FeatureTogglesInventory,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey.contains("cohort_")

    override suspend fun evaluate(paramKey: String): String {
        val experimentName = paramKey.split("_").getOrNull(2)
        if (experimentName.isNullOrBlank()) return ""

        val matchingExperiment = featureTogglesInventory
            .getAllActiveExperimentToggles()
            .firstOrNull { it.featureName().name == experimentName }

        return matchingExperiment?.let {
            "${it.featureName().name}_${it.getCohort()?.name}"
        }.orEmpty()
    }
}
