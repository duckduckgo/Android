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
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.UseOurAppNotification
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_SHOWN
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import com.duckduckgo.app.statistics.VariantManager
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

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
    private val useOurAppNotification: SchedulableNotification,
    private val variantManager: VariantManager,
    private val appInstallStore: AppInstallStore
) : AndroidNotificationScheduler {

    override suspend fun scheduleNextNotification() {
        scheduleUseOurAppNotification()
        scheduleInactiveUserNotifications()
    }

    private suspend fun scheduleUseOurAppNotification() {
        if (variant().hasFeature(VariantManager.VariantFeature.InAppUsage) && useOurAppNotification.canShow()) {
            val operation = scheduleUniqueNotification(
                OneTimeWorkRequestBuilder<UseOurAppNotificationWorker>(),
                UOA_DURATION,
                TimeUnit.DAYS,
                USE_OUR_APP_WORK_REQUEST_TAG
            )
            try {
                operation.await()
            } catch (e: Exception) {
                Timber.v("Notification could not be scheduled: $e")
            }
        }
    }

    private suspend fun scheduleInactiveUserNotifications() {
        workManager.cancelAllWorkByTag(UNUSED_APP_WORK_REQUEST_TAG)

        when {
            (!variant().hasFeature(VariantManager.VariantFeature.RemoveDay1AndDay3Notifications) && privacyNotification.canShow()) -> {
                val duration = getDurationForInactiveNotification(PRIVACY_DURATION)
                scheduleNotification(OneTimeWorkRequestBuilder<PrivacyNotificationWorker>(), duration, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            (!variant().hasFeature(VariantManager.VariantFeature.RemoveDay1AndDay3Notifications) && clearDataNotification.canShow()) -> {
                val duration = getDurationForInactiveNotification(CLEAR_DATA_DURATION)
                scheduleNotification(OneTimeWorkRequestBuilder<ClearDataNotificationWorker>(), duration, TimeUnit.DAYS, UNUSED_APP_WORK_REQUEST_TAG)
            }
            else -> Timber.v("Notifications not enabled for this variant")
        }
    }

    @VisibleForTesting
    fun getDurationForInactiveNotification(day: Long): Long {
        Timber.d("Inactive notification days installed is ${appInstallStore.daysInstalled()} day is $day")
        var duration = day
        if (variantHasInAppUsage() && (appInstallStore.daysInstalled() + day) == UOA_DURATION) {
            duration += 1
        }
        return duration
    }

    private fun variantHasInAppUsage() = variant().hasFeature(VariantManager.VariantFeature.InAppUsage)

    private fun variant() = variantManager.getVariant()

    private fun scheduleUniqueNotification(builder: OneTimeWorkRequest.Builder, duration: Long, unit: TimeUnit, tag: String): Operation {
        Timber.v("Scheduling unique notification")
        val request = builder
            .addTag(tag)
            .setInitialDelay(duration, unit)
            .build()

        return workManager.enqueueUniqueWork(tag, ExistingWorkPolicy.KEEP, request)
    }

    private fun scheduleNotification(builder: OneTimeWorkRequest.Builder, duration: Long, unit: TimeUnit, tag: String) {
        Timber.v("Scheduling notification for $duration")
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
        const val UOA_DURATION = 3L
        const val CLEAR_DATA_DURATION = 3L
        const val PRIVACY_DURATION = 1L
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class ClearDataNotificationWorkerInjectorPlugin @Inject constructor(
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationDao: NotificationDao,
    private val notificationFactory: NotificationFactory,
    private val pixel: Pixel,
    private val clearDataNotification: ClearDataNotification
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is NotificationScheduler.ClearDataNotificationWorker) {
            worker.manager = notificationManagerCompat
            worker.notificationDao = notificationDao
            worker.factory = notificationFactory
            worker.pixel = pixel
            worker.notification = clearDataNotification
            return true
        }
        return false
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class PrivacyNotificationWorkerInjectorPlugin @Inject constructor(
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationDao: NotificationDao,
    private val notificationFactory: NotificationFactory,
    private val pixel: Pixel,
    private val privacyProtectionNotification: PrivacyProtectionNotification
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is NotificationScheduler.PrivacyNotificationWorker) {
            worker.manager = notificationManagerCompat
            worker.notificationDao = notificationDao
            worker.factory = notificationFactory
            worker.pixel = pixel
            worker.notification = privacyProtectionNotification
            return true
        }
        return false
    }
}
@ContributesMultibinding(AppObjectGraph::class)
class UseOurAppNotificationWorkerInjectorPlugin @Inject constructor(
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationDao: NotificationDao,
    private val notificationFactory: NotificationFactory,
    private val pixel: Pixel,
    private val useOurAppNotification: UseOurAppNotification
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is NotificationScheduler.UseOurAppNotificationWorker) {
            worker.manager = notificationManagerCompat
            worker.notificationDao = notificationDao
            worker.factory = notificationFactory
            worker.pixel = pixel
            worker.notification = useOurAppNotification
            return true
        }
        return false
    }
}
