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

package com.duckduckgo.app.survey.notification

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.SchedulableNotificationWorker
import com.duckduckgo.app.survey.api.SurveyRepository
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@WorkerThread
interface SurveyNotificationScheduler {
    suspend fun scheduleSurveyAvailableNotification(survey: Survey)
    suspend fun removeScheduledSurveyAvailableNotification()
}

@ContributesBinding(AppScope::class)
class SurveyNotificationSchedulerImpl @Inject constructor(
    private val workManager: WorkManager,
    private val surveyRepository: SurveyRepository,
    private val notificationManager: NotificationManagerCompat,
) : SurveyNotificationScheduler {
    override suspend fun scheduleSurveyAvailableNotification(survey: Survey) {
        workManager.cancelAllWorkByTag(SURVEY_WORK_REQUEST_TAG)

        when (val delayDuration = surveyRepository.remainingDaysForShowingSurvey(survey)) {
            0L -> {
                scheduleNotification(
                    OneTimeWorkRequestBuilder<SurveyAvailableNotificationWorker>(),
                    MIN_DELAY_DURATION_IN_SECONDS,
                    TimeUnit.SECONDS,
                    SURVEY_WORK_REQUEST_TAG,
                )
            }
            in 1..Long.MAX_VALUE -> {
                scheduleNotification(
                    OneTimeWorkRequestBuilder<SurveyAvailableNotificationWorker>(),
                    delayDuration,
                    TimeUnit.DAYS,
                    SURVEY_WORK_REQUEST_TAG,
                )
            }
        }
    }

    override suspend fun removeScheduledSurveyAvailableNotification() {
        workManager.cancelAllWorkByTag(SURVEY_WORK_REQUEST_TAG)
    }

    private fun scheduleNotification(
        builder: OneTimeWorkRequest.Builder,
        duration: Long,
        unit: TimeUnit,
        tag: String,
    ) {
        logcat(VERBOSE) { "Scheduling notification in $duration $unit" }
        val request = builder
            .addTag(tag)
            .setInitialDelay(duration, unit)
            .build()

        workManager.enqueue(request)
    }

    companion object {
        const val SURVEY_WORK_REQUEST_TAG = "com.duckduckgo.notification.schedulesurvey"
        private const val MIN_DELAY_DURATION_IN_SECONDS = 5L
    }
}

@ContributesWorker(AppScope::class)
class SurveyAvailableNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : SchedulableNotificationWorker<SurveyAvailableNotification>(context, params) {
    @Inject
    override lateinit var notificationSender: NotificationSender

    @Inject
    override lateinit var notification: SurveyAvailableNotification
}
