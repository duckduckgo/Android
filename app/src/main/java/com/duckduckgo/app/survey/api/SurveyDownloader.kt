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

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.survey.api.SurveyGroup.SurveyOption
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.NOT_ALLOCATED
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.networkprotection.impl.cohort.NetpCohortStore
import io.reactivex.Completable
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject
import logcat.LogPriority.VERBOSE
import logcat.logcat
import retrofit2.Response

class SurveyDownloader @Inject constructor(
    private val service: SurveyService,
    private val emailManager: EmailManager,
    private val surveyRepository: SurveyRepository,
    private val netpCohortStore: NetpCohortStore,
) {

    private fun getSurveyResponse(): Response<SurveyGroup?> {
        val callNetP = service.surveyNetPWaitlistBeta()
        val responseNetP = callNetP.execute()

        // Why? see https://app.asana.com/0/414730916066338/1201395604254213/f
        // check temporary NetP survey endpoint else fallback to v2 survey endpoint
        if (responseNetP.isSuccessful && responseNetP.body()?.id != null) {
            logcat(VERBOSE) { "Returning NetP response" }
            return responseNetP
        }

        val call = service.survey()
        logcat(VERBOSE) { "Returning v2 response" }
        return call.execute()
    }

    fun download(): Completable {
        return Completable.fromAction {
            logcat { "Downloading user survey data" }

            val response = getSurveyResponse()

            logcat { "Response received, success=${response.isSuccessful}" }

            if (!response.isSuccessful) {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }

            val surveyGroup = response.body()
            if (surveyGroup?.id == null) {
                logcat { "No survey received, deleting old unused surveys" }
                surveyRepository.deleteUnusedSurveys()
                return@fromAction
            }

            if (surveyRepository.surveyExists(surveyGroup.id)) {
                logcat { "Survey received already in db, ignoring ${surveyGroup.id}" }
                return@fromAction
            }

            logcat { "New survey received. Unused surveys cleared and new survey saved" }
            surveyRepository.deleteUnusedSurveys()
            val surveyOption = determineOption(surveyGroup.surveyOptions)

            val newSurvey = when {
                surveyOption != null ->
                    when {
                        canSurveyBeScheduled(surveyOption) -> Survey(
                            surveyGroup.id,
                            calculateUrlWithParameters(surveyOption),
                            surveyOption.installationDay,
                            SCHEDULED,
                        )
                        else -> null
                    }
                else -> Survey(surveyGroup.id, null, null, NOT_ALLOCATED)
            }

            newSurvey?.let {
                if (surveyRepository.isUserEligibleForSurvey(newSurvey)) {
                    logcat(VERBOSE) { "User eligible for survey, storing" }
                    surveyRepository.persistSurvey(newSurvey)
                }
            }
        }
    }

    private fun calculateUrlWithParameters(surveyOption: SurveyOption): String {
        val uri = surveyOption.url.toUri()

        val builder = Uri.Builder()
            .authority(uri.authority)
            .scheme(uri.scheme)
            .path(uri.path)
            .fragment(uri.fragment)

        surveyOption.urlParameters?.map {
            when {
                (SurveyUrlParameter.EmailCohortParam.parameter == it) -> builder.appendQueryParameter(it, emailManager.getCohort())
                else -> {
                    // NO OP
                }
            }
        }

        return builder.build().toString()
    }

    private fun canSurveyBeScheduled(surveyOption: SurveyOption): Boolean {
        return if (surveyOption.isEmailSignedInRequired == true) {
            emailManager.isSignedIn()
        } else if (surveyOption.isNetPOnboardedRequired == true) {
            val now = LocalDate.now()
            val days = netpCohortStore.cohortLocalDate?.let { cohortLocalDate ->
                ChronoUnit.DAYS.between(cohortLocalDate, now)
            } ?: 0
            logcat(VERBOSE) { "Days since netp enabled = $days" }
            return surveyOption.daysSinceNetPEnabled?.let { daysSinceNetPEnabled ->
                logcat(VERBOSE) { "Days required since NetP enabled = $daysSinceNetPEnabled" }
                days >= daysSinceNetPEnabled
            } ?: false
        } else {
            true
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
