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

package com.duckduckgo.app.survey.api

import com.duckduckgo.app.survey.api.SurveyGroup.SurveyOption
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.NOT_ALLOCATED
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import io.reactivex.Completable
import timber.log.Timber
import java.io.IOException
import java.util.*
import javax.inject.Inject

class SurveyDownloader @Inject constructor(
    private val service: SurveyService,
    private val surveyDao: SurveyDao
) {

    fun download(): Completable {

        return Completable.fromAction {

            Timber.d("Downloading user survey data")

            val call = service.survey()
            val response = call.execute()

            Timber.d("Response received, success=${response.isSuccessful}")

            if (!response.isSuccessful) {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }

            val surveyGroup = response.body()
            if (surveyGroup?.id == null) {
                Timber.d("No survey received, deleting old unused surveys")
                surveyDao.deleteUnusedSurveys()
                return@fromAction
            }

            if (surveyDao.exists(surveyGroup.id)) {
                Timber.d("Survey received already in db, ignoring")
                return@fromAction
            }

            Timber.d("New survey received. Unused surveys cleared and new survey saved")
            surveyDao.deleteUnusedSurveys()
            val surveyOption = determineOption(surveyGroup.surveyOptions)
            val newSurvey = when {
                surveyOption != null -> Survey(surveyGroup.id, surveyOption.url, surveyOption.installationDay, SCHEDULED)
                else -> Survey(surveyGroup.id, null, null, NOT_ALLOCATED)
            }
            surveyDao.insert(newSurvey)
        }
    }

    private fun determineOption(options: List<SurveyOption>): SurveyOption? {
        var current = 0.0
        val randomAllocation = Random().nextDouble()

        for (option: SurveyOption in options) {
            current += option.ratioOfUsersToShow
            if (randomAllocation <= current) {
                return option
            }
        }
        return null
    }
}
