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

package com.duckduckgo.app.survey.api

import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import javax.inject.Inject

interface SurveyRepository {
    fun isUserEligibleForSurvey(survey: Survey): Boolean
    fun remainingDaysForShowingSurvey(survey: Survey): Long
    fun getScheduledSurvey(): Survey
}

@ContributesBinding(AppScope::class)
class SurveyRepositoryImpl @Inject constructor(
    private val surveyDao: SurveyDao,
    private val userBrowserProperties: UserBrowserProperties,
) : SurveyRepository {

    override fun isUserEligibleForSurvey(survey: Survey): Boolean {
        if (survey.url == null) {
            return false
        }

        val eligibleLocale = ALLOWED_LOCALES.contains(Locale.getDefault())
        return validDaysInstalled(survey) && eligibleLocale
    }

    override fun remainingDaysForShowingSurvey(survey: Survey): Long {
        // Case for targeting users with app install > 30 days
        if (survey.daysInstalled == null && userBrowserProperties.daysSinceInstalled() >= SURVEY_DEFAULT_MIN_DAYS_INSTALLED) {
            return 0L
        }
        // Case for targeting all users
        if (survey.daysInstalled == SURVEY_NO_MIN_DAYS_INSTALLED_REQUIRED) {
            return 0L
        }
        // If any of the above, then remaining days since app install
        if (survey.daysInstalled != null) {
            return survey.daysInstalled - userBrowserProperties.daysSinceInstalled()
        }
        return -1
    }

    private fun validDaysInstalled(survey: Survey): Boolean {
        if (survey.daysInstalled == null && userBrowserProperties.daysSinceInstalled() < SURVEY_DEFAULT_MIN_DAYS_INSTALLED) {
            return false
        }
        if ((survey.daysInstalled ?: 0) - userBrowserProperties.daysSinceInstalled() < 0) {
            return false
        }
        return true
    }

    override fun getScheduledSurvey(): Survey {
        return surveyDao.getScheduled().last()
    }

    companion object {
        private val ALLOWED_LOCALES = listOf(Locale.US, Locale.UK, Locale.CANADA)
        const val SURVEY_DEFAULT_MIN_DAYS_INSTALLED = 30
        const val SURVEY_NO_MIN_DAYS_INSTALLED_REQUIRED = -1
    }
}
