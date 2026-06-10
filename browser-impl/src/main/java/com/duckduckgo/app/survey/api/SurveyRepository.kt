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

import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import com.duckduckgo.app.notification.NotificationRegistrar.NotificationId
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.DONE
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import javax.inject.Inject

interface SurveyRepository {
    fun isUserEligibleForSurvey(survey: Survey): Boolean
    fun remainingDaysForShowingSurvey(survey: Survey): Long
    fun shouldShowSurvey(survey: Survey): Boolean
    fun getScheduledSurvey(): Survey
    fun getScheduledLiveSurvey(): LiveData<Survey>
    fun persistSurvey(survey: Survey)
    fun surveyExists(surveyId: String): Boolean
    fun updateSurvey(survey: Survey)
    fun cancelScheduledSurveys()
    fun deleteUnusedSurveys()
    fun clearSurveyNotification()
}

@ContributesBinding(AppScope::class)
class SurveyRepositoryImpl @Inject constructor(
    private val surveyDao: SurveyDao,
    private val userBrowserProperties: UserBrowserProperties,
    private val notificationManager: NotificationManagerCompat,
    private val appBuildConfig: AppBuildConfig,
) : SurveyRepository {

    override fun isUserEligibleForSurvey(survey: Survey): Boolean {
        if (survey.url == null) {
            return false
        }

        val eligibleLocale = ALLOWED_LOCALES.contains(appBuildConfig.deviceLocale)
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

    override fun shouldShowSurvey(survey: Survey): Boolean {
        return remainingDaysForShowingSurvey(survey) == 0L && survey.status != DONE
    }

    private fun validDaysInstalled(survey: Survey): Boolean {
        if (survey.daysInstalled == null && userBrowserProperties.daysSinceInstalled() < SURVEY_DEFAULT_MIN_DAYS_INSTALLED) {
            return false
        }
        // Case for targeting all users
        if (survey.daysInstalled == SURVEY_NO_MIN_DAYS_INSTALLED_REQUIRED) {
            return true
        }
        if ((survey.daysInstalled ?: 0) - userBrowserProperties.daysSinceInstalled() < 0) {
            return false
        }
        return true
    }

    override fun getScheduledSurvey(): Survey {
        return surveyDao.getScheduled().last()
    }

    override fun getScheduledLiveSurvey(): LiveData<Survey> {
        return surveyDao.getLiveScheduled()
    }

    override fun persistSurvey(survey: Survey) {
        surveyDao.insert(survey)
    }

    override fun surveyExists(surveyId: String): Boolean {
        return surveyDao.get(surveyId) != null
    }

    override fun updateSurvey(survey: Survey) {
        surveyDao.update(survey)
    }

    override fun cancelScheduledSurveys() {
        surveyDao.cancelScheduledSurveys()
    }

    override fun deleteUnusedSurveys() {
        surveyDao.deleteUnusedSurveys()
    }

    override fun clearSurveyNotification() {
        notificationManager.cancel(NotificationId.SurveyAvailable)
    }

    companion object {
        private val ALLOWED_LOCALES = listOf(Locale.US, Locale.UK, Locale.CANADA)
        private const val SURVEY_DEFAULT_MIN_DAYS_INSTALLED = 30
        private const val SURVEY_NO_MIN_DAYS_INSTALLED_REQUIRED = -1
    }
}
