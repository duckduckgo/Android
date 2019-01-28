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
import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.store.NotificationDao
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NotificationDayOne
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NotificationDayThree
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NotificationScheduler @Inject constructor(val dao: NotificationDao, val variantManager: VariantManager) {

    fun scheduleClearDataNotification() {
        WorkManager.getInstance().cancelAllWorkByTag(WORK_REQUEST_TAG)

        val day1 = variantManager.getVariant().hasFeature(NotificationDayOne)
        val day3 = variantManager.getVariant().hasFeature(NotificationDayThree)
        if (!day1 && !day3) {
            Timber.i("Notification not enabled for this variant")
            return
        }

        Schedulers.io().scheduleDirect {
            if (dao.exists(NotificationGenerator.NotificationSpecs.autoClear.id)) {
                Timber.i("Notification already seen, no need to schedule")
                return@scheduleDirect
            }

            val days: Long = if (day1) 1 else 3
            val request = OneTimeWorkRequestBuilder<ShowClearDataNotification>()
                .addTag(WORK_REQUEST_TAG)
                .setInitialDelay(days, TimeUnit.DAYS)

            WorkManager.getInstance().enqueue(request.build())
        }
    }

    class ShowClearDataNotification(val context: Context, params: WorkerParameters) : Worker(context, params) {

        lateinit var manager: NotificationManager
        lateinit var notificationDao: NotificationDao

        override fun doWork(): Result {
            val generator = NotificationGenerator(context)
            val specification = NotificationGenerator.NotificationSpecs.autoClear
            val notification = generator.buildNotification(manager, specification)
            notificationDao.insert(Notification(specification.id))
            manager.notify(specification.systemId, notification)
            return Result.SUCCESS
        }
    }

    companion object {
        const val WORK_REQUEST_TAG = "com.duckduckgo.notifications"
    }
}