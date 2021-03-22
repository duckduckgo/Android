/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.*
import com.duckduckgo.app.global.plugins.app.AppLifecycleObserverPlugin
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.analytics.DeviceShieldAnalytics
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Module
@ContributesTo(AppObjectGraph::class)
class DeviceShieldNotificationSchedulerModule {
    @Provides
    @IntoSet
    fun provideDeviceShieldNotificationScheduler(
        workManager: WorkManager
    ): AppLifecycleObserverPlugin {
        return DeviceShieldNotificationScheduler(workManager)
    }

    @Provides
    @IntoSet
    fun provideDeviceShieldNotificationWorkerInjectorPlugin(
        dailyNotificationPressedHandler: DailyNotificationPressedHandler,
        weeklyNotificationPressedHandler: WeeklyNotificationPressedHandler,
        deviceShieldAnalytics: DeviceShieldAnalytics,
        repository: AppTrackerBlockingStatsRepository,
        notificationManagerCompat: NotificationManagerCompat,
        deviceShieldNotificationFactory: DeviceShieldNotificationFactory
    ): WorkerInjectorPlugin {
        return DeviceShieldNotificationWorkerInjectorPlugin(
            dailyNotificationPressedHandler,
            weeklyNotificationPressedHandler,
            deviceShieldAnalytics,
            repository,
            notificationManagerCompat,
            deviceShieldNotificationFactory
        )
    }
}

class DeviceShieldNotificationScheduler(private val workManager: WorkManager) : AppLifecycleObserverPlugin {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun scheduleUsageNotification() {
        scheduleDailyNotification()
        scheduleWeeklyNotification()
    }

    private fun scheduleDailyNotification() {
        Timber.v("Scheduling the Vpn Daily notifications worker")
        for (i in 1..TOTAL_DAILY_NOTIFICATIONS) {
            Timber.v("Scheduling the Vpn Daily notification $i")
            val dailyNotificationRequest = createDailyNotificationRequest(i)
            workManager.enqueueUniqueWork(WORKER_VPN_DAILY_NOTIFICATION_NAME + i, ExistingWorkPolicy.KEEP, dailyNotificationRequest)
        }
    }

    private fun createDailyNotificationRequest(initialDelayDays: Long): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<DeviceShieldDailyNotificationWorker>()
            .addTag(WORKER_VPN_DAILY_NOTIFICATION_TAG)
            .setInitialDelay(initialDelayDays, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
    }

    private fun scheduleWeeklyNotification() {
        Timber.v("Scheduling the Vpn Weekly notifications worker")

        val weeklyNotificationRequest = PeriodicWorkRequestBuilder<DeviceShieldWeeklyNotificationWorker>(2, TimeUnit.MINUTES)
            .addTag(WORKER_VPN_WEEKLY_NOTIFICATION_TAG)
            .setInitialDelay(FIRST_WEEKLY_NOTIFICATION_DELAY, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(WORKER_VPN_WEEKLY_NOTIFICATION_NAME, ExistingPeriodicWorkPolicy.KEEP, weeklyNotificationRequest)
    }

    class DeviceShieldDailyNotificationWorker(val context: Context, val params: WorkerParameters) : CoroutineWorker(context, params) {
        lateinit var notificationPressedHandler: DailyNotificationPressedHandler
        lateinit var deviceShieldAnalytics: DeviceShieldAnalytics
        lateinit var repository: AppTrackerBlockingStatsRepository
        lateinit var notificationManager: NotificationManagerCompat
        lateinit var deviceShieldNotificationFactory: DeviceShieldNotificationFactory

        override suspend fun doWork(): Result {
            Timber.v("Vpn Daily notification worker is now awake")

            val deviceShieldNotification = deviceShieldNotificationFactory.createDailyDeviceShieldNotification().also {
                notificationPressedHandler.notificationVariant = it.notificationVariant
            }
            if (!deviceShieldNotification.hidden) {
                val notification = DeviceShieldAlertNotificationBuilder.buildDeviceShieldNotification(context, deviceShieldNotification, notificationPressedHandler)
                deviceShieldAnalytics.didShowDailyNotification(deviceShieldNotification.notificationVariant)
                notificationManager.notify(VPN_DAILY_NOTIFICATION_ID, notification)
            } else {
                Timber.v("Vpn Daily notification won't be shown because there is no data to show")
            }

            return Result.success()
        }
    }

    class DeviceShieldWeeklyNotificationWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        lateinit var notificationPressedHandler: WeeklyNotificationPressedHandler
        lateinit var deviceShieldAnalytics: DeviceShieldAnalytics
        lateinit var notificationManager: NotificationManagerCompat
        lateinit var deviceShieldNotificationFactory: DeviceShieldNotificationFactory

        override suspend fun doWork(): Result {
            Timber.v("Vpn Weekly notification worker is now awake")

            val deviceShieldNotification = deviceShieldNotificationFactory.createWeeklyDeviceShieldNotification().also {
                notificationPressedHandler.notificationVariant = it.notificationVariant
            }

            if (!deviceShieldNotification.hidden) {
                Timber.v("Vpn Daily notification won't be shown because there is no data to show")
                val notification = DeviceShieldAlertNotificationBuilder.buildDeviceShieldNotification(
                    context, deviceShieldNotification, notificationPressedHandler
                )
                deviceShieldAnalytics.didShowWeeklyNotification(deviceShieldNotification.notificationVariant)
                notificationManager.notify(VPN_WEEKLY_NOTIFICATION_ID, notification)
            }

            return Result.success()
        }
    }

    companion object {
        private const val WORKER_VPN_DAILY_NOTIFICATION_TAG = "VpnDailyNotificationWorker"
        private const val WORKER_VPN_DAILY_NOTIFICATION_NAME = "VpnDailyNotification"
        private const val WORKER_VPN_WEEKLY_NOTIFICATION_TAG = "VpnWeeklyNotificationWorker"
        private const val WORKER_VPN_WEEKLY_NOTIFICATION_NAME = "VpnWeeklyNotification"

        const val TOTAL_DAILY_NOTIFICATIONS = 7L
        const val FIRST_WEEKLY_NOTIFICATION_DELAY = 14L

        const val VPN_DAILY_NOTIFICATION_ID = 998
        const val VPN_WEEKLY_NOTIFICATION_ID = 997
    }
}

class DeviceShieldNotificationWorkerInjectorPlugin(
    private var dailyNotificationPressedHandler: DailyNotificationPressedHandler,
    private var weeklyNotificationPressedHandler: WeeklyNotificationPressedHandler,
    private var deviceShieldAnalytics: DeviceShieldAnalytics,
    private val repository: AppTrackerBlockingStatsRepository,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val deviceShieldNotificationFactory: DeviceShieldNotificationFactory
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is DeviceShieldNotificationScheduler.DeviceShieldDailyNotificationWorker) {
            worker.deviceShieldNotificationFactory = deviceShieldNotificationFactory
            worker.repository = repository
            worker.notificationManager = notificationManagerCompat
            worker.deviceShieldAnalytics = deviceShieldAnalytics
            worker.notificationPressedHandler = dailyNotificationPressedHandler
            return true
        }

        if (worker is DeviceShieldNotificationScheduler.DeviceShieldWeeklyNotificationWorker) {
            worker.deviceShieldNotificationFactory = deviceShieldNotificationFactory
            worker.deviceShieldAnalytics = deviceShieldAnalytics
            worker.notificationPressedHandler = weeklyNotificationPressedHandler
            return true
        }

        return false
    }
}
