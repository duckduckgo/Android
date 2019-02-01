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

import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_NONE
import android.app.PendingIntent
import android.app.PendingIntent.getService
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.NotificationFactory.NotificationSpec
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_CANCELLED
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_LAUNCHED
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NotificationDayOne
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NotificationDayThree
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.NOTIFICATIONS_SHOWN
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class NotificationScheduler @Inject constructor(
    val dao: NotificationDao,
    val compatManager: NotificationManagerCompat,
    val manager: NotificationManager,
    val settingsDataStore: SettingsDataStore,
    val variantManager: VariantManager,
    val pixel: Pixel
) {

    object Channels {
        val privacyTips = NotificationFactory.Channel(
            "com.duckduckgo.privacytips",
            "Privacy Tips",
            "Displays helpful privacy tips",
            IMPORTANCE_DEFAULT
        )
    }

    object NotificationSpecs {
        val autoClear = NotificationSpec(
            1,
            "com.duckduckgo.privacytips.autoclear",
            Channels.privacyTips,
            "Update auto clear data",
            R.drawable.notification_fire,
            R.drawable.notification_fire_legacy,
            R.string.clearNotificationTitle,
            R.string.clearNotificationDescription
        )
    }

    fun scheduleNextNotification() {
        WorkManager.getInstance().cancelAllWorkByTag(WORK_REQUEST_TAG)
        updateNotificationsStatus()

        val duration = when {
            variantManager.getVariant().hasFeature(NotificationDayOne) -> 1
            variantManager.getVariant().hasFeature(NotificationDayThree) -> 3
            else -> {
                Timber.v("Notifications not enabled for this variant")
                return
            }
        }
        scheduleClearDataNotification(duration.toLong(), TimeUnit.DAYS)
    }

    private fun updateNotificationsStatus() {
        val systemEnabled = compatManager.areNotificationsEnabled()
        val channelEnabled = when {
            SDK_INT >= O -> manager.getNotificationChannel(Channels.privacyTips.id)?.importance != IMPORTANCE_NONE
            else -> true
        }
        updateNotificationStatus(systemEnabled && channelEnabled)
    }

    fun updateNotificationStatus(enabled: Boolean) {
        if (settingsDataStore.appNotificationsEnabled != enabled) {
            pixel.fire(if (enabled) Pixel.PixelName.NOTIFICATIONS_ENABLED else Pixel.PixelName.NOTIFICATIONS_DISABLED)
            settingsDataStore.appNotificationsEnabled = enabled
        }
    }

    private fun scheduleClearDataNotification(duration: Long, unit: TimeUnit): Boolean {

        if (settingsDataStore.automaticallyClearWhatOption != ClearWhatOption.CLEAR_NONE) {
            Timber.v("No need for notification, user already has clear option set")
            return true
        }

        Schedulers.io().scheduleDirect {
            if (dao.exists(NotificationSpecs.autoClear.id)) {
                Timber.v("Clear data notification already seen, no need to schedule")
                return@scheduleDirect
            }

            Timber.v("Scheduling clear data notification")
            val request = OneTimeWorkRequestBuilder<ShowClearDataNotification>()
                .addTag(WORK_REQUEST_TAG)
                .setInitialDelay(duration, unit)

            WorkManager.getInstance().enqueue(request.build())
        }
        return false
    }

    class ShowClearDataNotification(val context: Context, params: WorkerParameters) : Worker(context, params) {

        lateinit var manager: NotificationManager
        lateinit var factory: NotificationFactory
        lateinit var notificationDao: NotificationDao
        lateinit var pixel: Pixel

        override fun doWork(): Result {

            val specification = NotificationSpecs.autoClear
            val launchIntent = pendingNotificationHandlerIntent(context, CLEAR_DATA_LAUNCHED)
            val cancelIntent = pendingNotificationHandlerIntent(context, CLEAR_DATA_CANCELLED)

            val notification = factory.createNotification(specification, launchIntent, cancelIntent)
            notificationDao.insert(Notification(specification.id))
            manager.notify(specification.systemId, notification)
            pixel.fire(NOTIFICATIONS_SHOWN)

            return Result.SUCCESS
        }

        private fun pendingNotificationHandlerIntent(context: Context, eventType: String): PendingIntent {
            val intent = Intent(context, NotificationHandlerService::class.java)
            intent.type = eventType
            return getService(context, 0, intent, 0)!!
        }
    }

    companion object {
        const val WORK_REQUEST_TAG = "com.duckduckgo.notifications"
    }
}