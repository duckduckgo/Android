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

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.useOurAppNotification
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.DripNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1DripB2Notification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1DripB1Notification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1DripA2Notification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1DripA1Notification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1PrivacyNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day3ClearDataNotification
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.NOTIFICATION_SHOWN
import timber.log.Timber
import java.util.concurrent.TimeUnit

// Please don't rename any Worker class name or class path
// More information: https://craigrussell.io/2019/04/a-workmanager-pitfall-modifying-a-scheduled-worker/
@WorkerThread
interface AndroidNotificationScheduler {
    suspend fun scheduleNextNotification()
}

class NotificationScheduler(
    private val workManager: WorkManager,
    private val clearDataNotification: SchedulableNotification,
    private val privacyNotification: SchedulableNotification,
    private val dripA1Notification: SchedulableNotification,
    private val dripA2Notification: SchedulableNotification,
    private val dripB1Notification: SchedulableNotification,
    private val dripB2Notification: SchedulableNotification,
    private val variantManager: VariantManager,
    private val userStageStore: UserStageStore
) : AndroidNotificationScheduler {

    override suspend fun scheduleNextNotification() {
        scheduleUseOurAppNotification()
        scheduleInactiveUserNotifications()
    }

    private suspend fun scheduleUseOurAppNotification() {
        if (userStageStore.useOurAppNotification()) {
            val operation = scheduleUniqueNotification(
                OneTimeWorkRequestBuilder<UseOurAppNotificationWorker>(),
                1,
                TimeUnit.DAYS,
                USE_OUR_APP_WORK_REQUEST_TAG
            )
            try {
                operation.await()
                userStageStore.stageCompleted(AppStage.USE_OUR_APP_NOTIFICATION)
            } catch (e: Exception) {
                Timber.v("Notification could not be scheduled: $e")
            }
        }
    }

    private suspend fun scheduleInactiveUserNotifications() {
        workManager.cancelAllWorkByTag(UNUSED_APP_WORK_REQUEST_TAG)

        when {
            variant().hasFeature(Day1DripA1Notification) && dripA1Notification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<DripA1NotificationWorker>(), 1, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            variant().hasFeature(Day1DripA2Notification) && dripA2Notification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<DripA2NotificationWorker>(), 1, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            variant().hasFeature(Day1DripB1Notification) && dripB1Notification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<DripB1NotificationWorker>(), 1, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            variant().hasFeature(Day1DripB2Notification) && dripB2Notification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<DripB2NotificationWorker>(), 1, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            (isNotDripVariant() || isDripVariantAndHasPrivacyFeature()) && privacyNotification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<PrivacyNotificationWorker>(), 1, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            (isNotDripVariant() || isDripVariantAndHasClearDataFeature()) && clearDataNotification.canShow() -> {
                scheduleNotification(OneTimeWorkRequestBuilder<ClearDataNotificationWorker>(), 3, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            else -> Timber.v("Notifications not enabled for this variant")
        }
    }

    private fun variant() = variantManager.getVariant()

    private fun isDripVariantAndHasPrivacyFeature(): Boolean = isFromDripNotificationVariant() && variant().hasFeature(Day1PrivacyNotification)

    private fun isDripVariantAndHasClearDataFeature(): Boolean = isFromDripNotificationVariant() && variant().hasFeature(Day3ClearDataNotification)

    private fun isFromDripNotificationVariant(): Boolean = variant().hasFeature(DripNotification)

    private fun isNotDripVariant(): Boolean = !variant().hasFeature(DripNotification)

    private fun scheduleUniqueNotification(builder: OneTimeWorkRequest.Builder, duration: Long, unit: TimeUnit, tag: String): Operation {
        Timber.v("Scheduling unique notification")
        val request = builder
            .addTag(tag)
            .setInitialDelay(duration, unit)
            .build()

        return workManager.enqueueUniqueWork(tag, ExistingWorkPolicy.KEEP, request)
    }

    private fun scheduleNotification(builder: OneTimeWorkRequest.Builder, duration: Long, unit: TimeUnit, tag: String) {
        Timber.v("Scheduling notification")
        val request = builder
            .addTag(tag)
            .setInitialDelay(duration, unit)
            .build()

        workManager.enqueue(request)
    }

    // Legacy code. Unused class required for users who already have this notification scheduled from previous version. We will
    // delete this as part of https://app.asana.com/0/414730916066338/1119619712088571
    class ShowClearDataNotification(context: Context, params: WorkerParameters) : ClearDataNotificationWorker(context, params)

    open class ClearDataNotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class PrivacyNotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class DripA1NotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class DripA2NotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class DripB1NotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class DripB2NotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)
    class UseOurAppNotificationWorker(context: Context, params: WorkerParameters) : SchedulableNotificationWorker(context, params)

    open class SchedulableNotificationWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        lateinit var manager: NotificationManagerCompat
        lateinit var factory: NotificationFactory
        lateinit var notification: SchedulableNotification
        lateinit var notificationDao: NotificationDao
        lateinit var pixel: Pixel

        override suspend fun doWork(): Result {

            if (!notification.canShow()) {
                Timber.v("Notification no longer showable")
                return Result.success()
            }

            val specification = notification.buildSpecification()
            val launchIntent = NotificationHandlerService.pendingNotificationHandlerIntent(context, notification.launchIntent, specification)
            val cancelIntent = NotificationHandlerService.pendingNotificationHandlerIntent(context, notification.cancelIntent, specification)
            val systemNotification = factory.createNotification(specification, launchIntent, cancelIntent)
            notificationDao.insert(Notification(notification.id))
            manager.notify(specification.systemId, systemNotification)

            pixel.fire("${NOTIFICATION_SHOWN.pixelName}_${specification.pixelSuffix}")
            return Result.success()
        }
    }

    companion object {
        const val UNUSED_APP_WORK_REQUEST_TAG = "com.duckduckgo.notification.schedule"
        const val USE_OUR_APP_WORK_REQUEST_TAG = "com.duckduckgo.notification.useOurApp"
    }
}
