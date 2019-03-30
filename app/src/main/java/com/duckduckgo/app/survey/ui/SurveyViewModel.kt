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

import android.os.Build
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import io.reactivex.schedulers.Schedulers


class SurveyViewModel(
    private val surveyDao: SurveyDao,
    private val statisticsStore: StatisticsDataStore,
    private val appInstallStore: AppInstallStore
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
        return url.toUri()
            .buildUpon()
            .appendQueryParameter(SurveyParams.ATB, statisticsStore.atb?.version ?: "")
            .appendQueryParameter(SurveyParams.ATB_VARIANT, statisticsStore.variant)
            .appendQueryParameter(SurveyParams.DAYS_INSTALLED, "${appInstallStore.daysInstalled()}")
            .appendQueryParameter(SurveyParams.ANDROID_VERSION, "${Build.VERSION.SDK_INT}")
            .appendQueryParameter(SurveyParams.APP_VERSION, BuildConfig.VERSION_NAME)
            .appendQueryParameter(SurveyParams.MANUFACTURER, Build.MANUFACTURER)
            .appendQueryParameter(SurveyParams.MODEL, Build.MODEL)
            .build()
            .toString()
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
        Schedulers.io().scheduleDirect {
            surveyDao.update(survey)
        }
        command.value = Command.Close
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