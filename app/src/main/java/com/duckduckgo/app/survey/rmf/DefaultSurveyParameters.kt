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
import com.duckduckgo.survey.api.SurveyParameterPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AtbSurveyParameterPlugin @Inject constructor(
    private val statisticsStore: StatisticsDataStore,
) : SurveyParameterPlugin {
    override val surveyParamKey: String = "atb"

    override suspend fun evaluate(): String = statisticsStore.atb?.version ?: ""
}

@ContributesMultibinding(AppScope::class)
class AtbVariantSurveyParameterPlugin @Inject constructor(
    private val statisticsStore: StatisticsDataStore,
) : SurveyParameterPlugin {
    override val surveyParamKey: String = "var"

    override suspend fun evaluate(): String = statisticsStore.variant ?: ""
}

@ContributesMultibinding(AppScope::class)
class DaysInstalledSurveyParameterPlugin @Inject constructor(
    private val appInstallStore: AppInstallStore,
) : SurveyParameterPlugin {
    override val surveyParamKey: String = "delta"

    override suspend fun evaluate(): String = "${appInstallStore.daysInstalled()}"
}

@ContributesMultibinding(AppScope::class)
class AndroidVersionSurveyParameterPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SurveyParameterPlugin {
    override val surveyParamKey: String = "av"

    override suspend fun evaluate(): String = "${appBuildConfig.sdkInt}"
}

@ContributesMultibinding(AppScope::class)
class AppVersionSurveyParameterPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SurveyParameterPlugin {
    override val surveyParamKey: String = "ddgv"

    override suspend fun evaluate(): String = appBuildConfig.versionName
}

@ContributesMultibinding(AppScope::class)
class ManufacturerSurveyParameterPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SurveyParameterPlugin {
    override val surveyParamKey: String = "man"

    override suspend fun evaluate(): String = appBuildConfig.manufacturer
}

@ContributesMultibinding(AppScope::class)
class ModelSurveyParameterPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SurveyParameterPlugin {
    override val surveyParamKey: String = "mo"

    override suspend fun evaluate(): String = appBuildConfig.model
}

@ContributesMultibinding(AppScope::class)
class SourceSurveyParameterPlugin @Inject constructor() : SurveyParameterPlugin {
    override val surveyParamKey: String = "src"

    override suspend fun evaluate(): String = IN_APP.name.lowercase()
}

@ContributesMultibinding(AppScope::class)
class LastActiveDateSurveyParameterPlugin @Inject constructor(
    private val appDaysUsedRepository: AppDaysUsedRepository,
) : SurveyParameterPlugin {
    override val surveyParamKey: String = "da"

    override suspend fun evaluate(): String = appDaysUsedRepository.getLastActiveDay()
}
