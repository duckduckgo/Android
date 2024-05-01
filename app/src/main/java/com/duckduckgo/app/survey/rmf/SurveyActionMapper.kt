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

import androidx.core.net.toUri
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.survey.ui.SurveyActivity.Companion.SurveySource.IN_APP
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.JsonActionType.SURVEY
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@ContributesMultibinding(
    AppScope::class,
)
class SurveyActionMapper @Inject constructor(
    private val statisticsStore: StatisticsDataStore,
    private val appInstallStore: AppInstallStore,
    private val appBuildConfig: AppBuildConfig,
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val dispatcherProvider: DispatcherProvider,
) : MessageActionMapperPlugin {

    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        if (jsonMessageAction.type == SURVEY.jsonValue) {
            val queryParams = jsonMessageAction.additionalParameters?.get("queryParams")?.split(";") ?: emptyList()
            val knownParams = SurveyParams.values().map { it.queryParamName }
            val canAppendAllParams = knownParams.containsAll(queryParams)
            if (canAppendAllParams) {
                val url = runBlocking(dispatcherProvider.io()) {
                    jsonMessageAction.value.addSurveyParameters(queryParams)
                }
                return Action.Survey(url, jsonMessageAction.additionalParameters)
            }
        }
        return null
    }

    private suspend fun String.addSurveyParameters(queryParams: List<String>): String {
        val urlBuilder = toUri().buildUpon()

        queryParams.forEach { key ->
            when (key) {
                SurveyParams.ATB.queryParamName -> urlBuilder.appendQueryParameter(
                    SurveyParams.ATB.queryParamName,
                    statisticsStore.atb?.version ?: "",
                )

                SurveyParams.ATB_VARIANT.queryParamName -> urlBuilder.appendQueryParameter(
                    SurveyParams.ATB_VARIANT.queryParamName,
                    statisticsStore.variant,
                )

                SurveyParams.DAYS_INSTALLED.queryParamName -> urlBuilder.appendQueryParameter(
                    SurveyParams.DAYS_INSTALLED.queryParamName,
                    "${appInstallStore.daysInstalled()}",
                )

                SurveyParams.ANDROID_VERSION.queryParamName -> urlBuilder.appendQueryParameter(
                    SurveyParams.ANDROID_VERSION.queryParamName,
                    "${appBuildConfig.sdkInt}",
                )

                SurveyParams.APP_VERSION.queryParamName -> urlBuilder.appendQueryParameter(
                    SurveyParams.APP_VERSION.queryParamName,
                    appBuildConfig.versionName,
                )

                SurveyParams.MANUFACTURER.queryParamName -> urlBuilder.appendQueryParameter(
                    SurveyParams.MANUFACTURER.queryParamName,
                    appBuildConfig.manufacturer,
                )

                SurveyParams.MODEL.queryParamName -> urlBuilder.appendQueryParameter(SurveyParams.MODEL.queryParamName, appBuildConfig.model)
                SurveyParams.SOURCE.queryParamName -> urlBuilder.appendQueryParameter(SurveyParams.SOURCE.queryParamName, IN_APP.name.lowercase())
                SurveyParams.LAST_ACTIVE_DATE.queryParamName -> urlBuilder.appendQueryParameter(
                    SurveyParams.LAST_ACTIVE_DATE.queryParamName,
                    appDaysUsedRepository.getLastActiveDay(),
                )
            }
        }
        return urlBuilder.build().toString()
    }

    private enum class SurveyParams(val queryParamName: String) {
        ATB("atb"),
        ATB_VARIANT("var"),
        DAYS_INSTALLED("delta"),
        ANDROID_VERSION("av"),
        APP_VERSION("ddgv"),
        MANUFACTURER("man"),
        MODEL("mo"),
        LAST_ACTIVE_DATE("da"),
        SOURCE("src"),
    }
}
