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
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.app.TaskStackBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.NotificationGenerator.NotificationSpec
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.store.NotificationDao
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NotificationDayOne
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NotificationDayThree
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class NotificationScheduler @Inject constructor(
    val dao: NotificationDao,
    val settingsDataStore: SettingsDataStore,
    val variantManager: VariantManager
) {

    object Channels {

        val privacyTips = NotificationGenerator.Channel(
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

    fun scheduleClearDataNotification() {

        WorkManager.getInstance().cancelAllWorkByTag(WORK_REQUEST_TAG)

        Timber.v("Scheduling clear data notification")

        val day1 = variantManager.getVariant().hasFeature(NotificationDayOne)
        val day3 = variantManager.getVariant().hasFeature(NotificationDayThree)
        if (!day1 && !day3) {
            Timber.v("Notifications not enabled for this variant")
            return
        }

        if (settingsDataStore.automaticallyClearWhatOption != ClearWhatOption.CLEAR_NONE) {
            Timber.v("No need for notification, user already has clear option set")
            return
        }

        Schedulers.io().scheduleDirect {
            if (dao.exists(NotificationSpecs.autoClear.id)) {
                Timber.v("Notification already seen, no need to schedule")
                return@scheduleDirect
            }

            val request = OneTimeWorkRequestBuilder<ShowClearDataNotification>()
                .addTag(WORK_REQUEST_TAG)
                .setInitialDelay(if (day1) 1 else 3, TimeUnit.DAYS)

            WorkManager.getInstance().enqueue(request.build())
        }
    }

    class ShowClearDataNotification(val context: Context, params: WorkerParameters) : Worker(context, params) {

        lateinit var manager: NotificationManager
        lateinit var generator: NotificationGenerator
        lateinit var notificationDao: NotificationDao

        override fun doWork(): Result {

            val specification = NotificationSpecs.autoClear
            val pendingIntent = buildPendingIntent(SettingsActivity.intent(context))
            val notification = generator.createNotification(manager, specification, pendingIntent)

            notificationDao.insert(Notification(specification.id))
            manager.notify(specification.systemId, notification)

            return Result.SUCCESS
        }

        private fun buildPendingIntent(intent: Intent): PendingIntent {
            val stackBuilder = TaskStackBuilder.create(context.applicationContext)
            stackBuilder.addNextIntentWithParentStack(intent)
            return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)!!
        }
    }


    companion object {
        const val WORK_REQUEST_TAG = "com.duckduckgo.notifications"
    }
}