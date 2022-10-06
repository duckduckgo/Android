/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.survey.ui

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class SurveyViewModel @Inject constructor(
    private val surveyDao: SurveyDao,
    private val statisticsStore: StatisticsDataStore,
    private val appInstallStore: AppInstallStore,
    private val appBuildConfig: AppBuildConfig,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    sealed class Command {
        class LoadSurvey(val url: String) : Command()
        object ShowError : Command()
        object ShowSurvey : Command()
        object Close : Command()
    }

    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    private lateinit var survey: Survey
    private var didError = false

    fun start(survey: Survey) {
        val url = survey.url ?: return
        this.survey = survey
        command.value = Command.LoadSurvey(addSurveyParameters(url))
    }

    private fun addSurveyParameters(url: String): String {
        val urlBuilder = url.toUri()
            .buildUpon()
            .appendQueryParameter(SurveyParams.ATB, statisticsStore.atb?.version ?: "")
            .appendQueryParameter(SurveyParams.ATB_VARIANT, statisticsStore.variant)
            .appendQueryParameter(SurveyParams.DAYS_INSTALLED, "${appInstallStore.daysInstalled()}")
            .appendQueryParameter(SurveyParams.ANDROID_VERSION, "${appBuildConfig.sdkInt}")
            .appendQueryParameter(SurveyParams.APP_VERSION, appBuildConfig.versionName)
            .appendQueryParameter(SurveyParams.MANUFACTURER, appBuildConfig.manufacturer)
            .appendQueryParameter(SurveyParams.MODEL, appBuildConfig.model)

        return urlBuilder.build().toString()
    }

    fun onSurveyFailedToLoad() {
        didError = true
        command.value = Command.ShowError
    }

    fun onSurveyLoaded() {
        if (!didError) {
            command.value = Command.ShowSurvey
        }
    }

    fun onSurveyCompleted() {
        survey.status = Survey.Status.DONE
        viewModelScope.launch {
            withContext(dispatchers.io() + NonCancellable) {
                surveyDao.update(survey)
            }
            withContext(dispatchers.main()) {
                command.value = Command.Close
            }
        }
    }

    fun onSurveyDismissed() {
        command.value = Command.Close
    }

    private object SurveyParams {
        const val ATB = "atb"
        const val ATB_VARIANT = "var"
        const val DAYS_INSTALLED = "delta"
        const val ANDROID_VERSION = "av"
        const val APP_VERSION = "ddgv"
        const val MANUFACTURER = "man"
        const val MODEL = "mo"
    }
}
