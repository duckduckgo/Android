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
import androidx.work.*
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.*
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.NOTIFICATION_SHOWN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NotificationScheduler @Inject constructor(
    val dao: NotificationDao,
    val manager: NotificationManagerCompat,
    val settingsDataStore: SettingsDataStore,
    val variantManager: VariantManager,
    val clearDataNotification: ClearDataNotification,
    val privacyNotification: PrivacyProtectionNotification
) {
    fun scheduleNextNotification(scope: CoroutineScope = GlobalScope) {

        WorkManager.getInstance().cancelAllWorkByTag(WORK_REQUEST_TAG)
        val variant = variantManager.getVariant()

        val timeUnit = TimeUnit.DAYS
        GlobalScope.launch {
            when {
                variant.hasFeature(NotificationPrivacyDay1) && privacyNotification.canShow() -> {
                    scheduleNotification(OneTimeWorkRequestBuilder<PrivacyNotificationWorker>(), 1, timeUnit, scope)
                }
                variant.hasFeature(NotificationClearDataDay1) && clearDataNotification.canShow() -> {
                    scheduleNotification(OneTimeWorkRequestBuilder<ClearDataNotificationWorker>(), 1, timeUnit, scope)
                }
                variant.hasFeature(NotificationClearDataDay3) && clearDataNotification.canShow() -> {
                    scheduleNotification(OneTimeWorkRequestBuilder<ClearDataNotificationWorker>(), 3, timeUnit, scope)
                }
                else -> Timber.v("Notifications not enabled for this variant")
            }
        }
    }

    private fun scheduleNotification(builder: OneTimeWorkRequest.Builder, duration: Long, unit: TimeUnit, scope: CoroutineScope) {
        scope.launch {
            Timber.v("Scheduling notification")
            val request = builder
                .addTag(WORK_REQUEST_TAG)
                .setInitialDelay(duration, unit)
                .build()

            WorkManager.getInstance().enqueue(request)
        }
    }

    class ClearDataNotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class PrivacyNotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)

    open class SchedulableNotificationWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {

        lateinit var manager: NotificationManagerCompat
        lateinit var factory: NotificationFactory
        lateinit var schedulableNotification: SchedulableNotification
        lateinit var notificationDao: NotificationDao
        lateinit var pixel: Pixel

        override fun doWork(): Result {

            val canShow = runBlocking { schedulableNotification.canShow() }
            if (!canShow) {
                Timber.v("Notification no longer showable")
                return Result.success()
            }

            val specification = schedulableNotification.specification
            val launchIntent = pendingNotificationHandlerIntent(context, schedulableNotification.launchIntent)
            val cancelIntent = pendingNotificationHandlerIntent(context, schedulableNotification.cancelIntent)
            val notification = factory.createNotification(specification, launchIntent, cancelIntent)
            notificationDao.insert(Notification(specification.id))
            manager.notify(specification.systemId, notification)

            pixel.fire(NOTIFICATION_SHOWN)
            return Result.success()
        }

        fun pendingNotificationHandlerIntent(context: Context, eventType: String): PendingIntent {
            val intent = Intent(context, NotificationHandlerService::class.java)
            intent.type = eventType
            return getService(context, 0, intent, 0)!!
        }
    }

    companion object {
        const val WORK_REQUEST_TAG = "com.duckduckgo.notification.schedule"
    }
}