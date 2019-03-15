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

package com.duckduckgo.app.notification

import android.app.PendingIntent
import android.app.PendingIntent.getService
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_CANCELLED
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_LAUNCHED
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.NOTIFICATIONS_SHOWN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class NotificationScheduler @Inject constructor(
    val dao: NotificationDao,
    val manager: NotificationManagerCompat,
    val settingsDataStore: SettingsDataStore
) {

    data class NotificationSpec(
        val systemId: Int,
        val id: String,
        val channel: NotificationRegistrar.Channel,
        val name: String,
        val icon: Int,
        val title: Int,
        val description: Int
    )

    object NotificationSpecs {
        val autoClear = NotificationSpec(
            100,
            "com.duckduckgo.privacytips.autoclear",
            NotificationRegistrar.ChannelType.PRIVACY_TIPS,
            "Update auto clear data",
            R.drawable.notification_fire,
            R.string.clearNotificationTitle,
            R.string.clearNotificationDescription
        )
    }

    fun scheduleNextNotification(scope: CoroutineScope = GlobalScope) {
        WorkManager.getInstance().cancelAllWorkByTag(WORK_REQUEST_TAG)
        scheduleClearDataNotification(SCHEDULE_AFTER_INACTIVE_DAYS, TimeUnit.DAYS, scope)
    }

    private fun scheduleClearDataNotification(duration: Long, unit: TimeUnit, scope: CoroutineScope) {

        if (settingsDataStore.automaticallyClearWhatOption != ClearWhatOption.CLEAR_NONE) {
            Timber.v("No need for notification, user already has clear option set")
            return
        }

        scope.launch {
            if (dao.exists(NotificationSpecs.autoClear.id)) {
                Timber.v("Clear data notification already seen, no need to schedule")
                return@launch
            }

            Timber.v("Scheduling clear data notification")
            val request = OneTimeWorkRequestBuilder<ShowClearDataNotification>()
                .addTag(WORK_REQUEST_TAG)
                .setInitialDelay(duration, unit)

            WorkManager.getInstance().enqueue(request.build())
        }
    }

    class ShowClearDataNotification(val context: Context, params: WorkerParameters) : Worker(context, params) {

        lateinit var manager: NotificationManagerCompat
        lateinit var factory: NotificationFactory
        lateinit var notificationDao: NotificationDao
        lateinit var settingsDataStore: SettingsDataStore
        lateinit var pixel: Pixel

        override fun doWork(): Result {

            if (settingsDataStore.automaticallyClearWhatOption != ClearWhatOption.CLEAR_NONE) {
                Timber.v("No need for notification, user already has clear option set")
                return Result.success()
            }

            val specification = NotificationSpecs.autoClear
            val launchIntent = pendingNotificationHandlerIntent(context, CLEAR_DATA_LAUNCHED)
            val cancelIntent = pendingNotificationHandlerIntent(context, CLEAR_DATA_CANCELLED)
            val notification = factory.createNotification(specification, launchIntent, cancelIntent)
            notificationDao.insert(Notification(specification.id))
            manager.notify(specification.systemId, notification)

            pixel.fire(NOTIFICATIONS_SHOWN)
            return Result.success()
        }

        private fun pendingNotificationHandlerIntent(context: Context, eventType: String): PendingIntent {
            val intent = Intent(context, NotificationHandlerService::class.java)
            intent.type = eventType
            return getService(context, 0, intent, 0)!!
        }
    }

    companion object {
        const val WORK_REQUEST_TAG = "com.duckduckgo.notification.schedule"
        const val SCHEDULE_AFTER_INACTIVE_DAYS = 3L
    }
}