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

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.TaskStackBuilderFactory
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.NotificationSpec
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.api.SurveyRepository
import com.duckduckgo.app.survey.ui.SurveyActivity
import com.duckduckgo.app.survey.ui.SurveyActivity.Companion.SurveySource
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SurveyAvailableNotification @Inject constructor(
    private val context: Context,
    private val notificationDao: NotificationDao,
) : SchedulableNotification {
    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ensure id is computed every time the caller gets it
    override val id
        get() = "com.duckduckgo.survey.availablesurvey${formatter.format((Date()))}"

    override suspend fun canShow(): Boolean {
        return !notificationDao.exists(id)
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return SurveyAvailableSpecification(context)
    }
}

class SurveyAvailableSpecification(context: Context) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.SurveyAvailable
    override val name = "Survey available"
    override val icon = com.duckduckgo.mobile.android.R.drawable.notification_logo
    override val title: String = context.getString(R.string.surveyCtaTitle)
    override val description: String = context.getString(R.string.surveyCtaDescription)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "SurveyAvailable"
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = com.duckduckgo.mobile.android.R.color.ic_launcher_red_background
}

@ContributesMultibinding(AppScope::class)
class AvailableSurveyNotificationPlugin @Inject constructor(
    private val context: Context,
    private val schedulableNotification: SurveyAvailableNotification,
    private val taskStackBuilderFactory: TaskStackBuilderFactory,
    private val pixel: Pixel,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val surveyRepository: SurveyRepository,
) : SchedulableNotificationPlugin {

    override fun getSchedulableNotification(): SchedulableNotification {
        return schedulableNotification
    }

    override fun onNotificationCancelled() {
        pixel.fire(pixelName(AppPixelName.NOTIFICATION_CANCELLED.pixelName))
    }

    override fun onNotificationShown() {
        pixel.fire(pixelName(AppPixelName.NOTIFICATION_SHOWN.pixelName))
    }

    override fun getSpecification(): NotificationSpec {
        val deferred = coroutineScope.async(dispatcherProvider.io()) {
            schedulableNotification.buildSpecification()
        }
        return runBlocking {
            deferred.await()
        }
    }

    override fun getLaunchIntent(): PendingIntent? {
        val intent = SurveyActivity.intent(
            context,
            surveyRepository.getScheduledSurvey(),
            SurveySource.PUSH,
            pixelName(AppPixelName.NOTIFICATION_LAUNCHED.pixelName),
        )
        val pendingIntent: PendingIntent? = taskStackBuilderFactory.createTaskBuilder().run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return pendingIntent
    }

    private fun pixelName(notificationType: String) = "${notificationType}_${getSpecification().pixelSuffix}"
}
