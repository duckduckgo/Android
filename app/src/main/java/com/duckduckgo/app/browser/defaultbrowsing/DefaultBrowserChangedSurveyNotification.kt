/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultbrowsing

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
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.ui.SurveyActivity
import com.duckduckgo.app.survey.ui.SurveyActivity.Companion.SurveySource
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DefaultBrowserChangedSurveyNotification @Inject constructor(
    private val context: Context,
    private val notificationDao: NotificationDao,
) : SchedulableNotification {

    override val id: String = NOTIFICATION_ID

    override suspend fun canShow(): Boolean {
        return !notificationDao.exists(id)
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return DefaultBrowserChangedSurveyNotificationSpec(context)
    }

    companion object {
        const val NOTIFICATION_ID = "com.duckduckgo.defaultbrowserchanged.survey"
    }
}

class DefaultBrowserChangedSurveyNotificationSpec(context: Context) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.DefaultBrowserChangedSurvey
    override val name = "Default browser changed survey"
    override val icon = com.duckduckgo.mobile.android.R.drawable.notification_logo
    override val title: String = context.getString(R.string.defaultBrowserChangedSurveyNotificationTitle)
    override val description: String = context.getString(R.string.defaultBrowserChangedSurveyNotificationDescription)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "DefaultBrowserChangedSurvey"
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = com.duckduckgo.mobile.android.R.color.ic_launcher_red_background
}

@ContributesMultibinding(AppScope::class)
class DefaultBrowserChangedSurveyNotificationPlugin @Inject constructor(
    private val context: Context,
    private val schedulableNotification: DefaultBrowserChangedSurveyNotification,
    private val taskStackBuilderFactory: TaskStackBuilderFactory,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SchedulableNotificationPlugin {

    override fun getSchedulableNotification(): SchedulableNotification {
        return schedulableNotification
    }

    override fun onNotificationCancelled() { }

    override fun onNotificationShown() { }

    override fun getSpecification(): NotificationSpec {
        val deferred = coroutineScope.async(dispatcherProvider.io()) {
            schedulableNotification.buildSpecification()
        }
        return runBlocking { deferred.await() }
    }

    override fun getLaunchIntent(): PendingIntent? {
        val survey = Survey(
            surveyId = "default-browser-changed-push",
            url = PUSH_SURVEY_URL,
            daysInstalled = null,
            status = Survey.Status.SCHEDULED,
        )
        val intent = SurveyActivity.intent(
            context,
            survey,
            SurveySource.PUSH,
        )
        return taskStackBuilderFactory.createTaskBuilder().run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    companion object {
        // TODO replace once survey URL available
        const val PUSH_SURVEY_URL = "https://example.com/test-survey?channel=push"
    }
}
