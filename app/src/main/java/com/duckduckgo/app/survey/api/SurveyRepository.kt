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
        val eligibleLocale = ALLOWED_LOCALES.contains(Locale.getDefault())
        return remainingDaysForShowingSurvey(survey) >= 0 && eligibleLocale
    }

    override fun remainingDaysForShowingSurvey(survey: Survey): Long {
        return (survey.daysInstalled ?: 0) - userBrowserProperties.daysSinceInstalled()
    }

    override fun getScheduledSurvey(): Survey {
        return surveyDao.getScheduled().last()
    }

    companion object {
        private val ALLOWED_LOCALES = listOf(Locale.US, Locale.UK, Locale.CANADA)
    }
}
