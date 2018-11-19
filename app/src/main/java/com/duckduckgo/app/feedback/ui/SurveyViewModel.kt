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

package com.duckduckgo.app.feedback.ui

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.feedback.db.SurveyDao
import com.duckduckgo.app.feedback.model.Survey
import com.duckduckgo.app.global.SingleLiveEvent
import io.reactivex.schedulers.Schedulers


class SurveyViewModel(private val surveyDao: SurveyDao) : ViewModel() {

    sealed class Command {
        class LoadSurvey(val url: String) : Command()
        object Close : Command()
    }

    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    private lateinit var survey: Survey

    fun start(survey: Survey) {
        val url = survey.url ?: return
        this.survey = survey
        command.value = Command.LoadSurvey(url)
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
}