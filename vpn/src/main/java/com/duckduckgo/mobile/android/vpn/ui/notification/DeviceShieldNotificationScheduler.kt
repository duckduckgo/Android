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
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.dao.VpnNotification
import com.duckduckgo.mobile.android.vpn.dao.VpnNotificationsDao
import com.duckduckgo.mobile.android.vpn.di.VpnCoroutineScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Module
@ContributesTo(AppObjectGraph::class)
class DeviceShieldNotificationSchedulerModule {
    @Provides
    @IntoSet
    fun provideDeviceShieldNotificationScheduler(
        @VpnCoroutineScope coroutineScope: CoroutineScope,
        workManager: WorkManager,
        vpnDatabase: VpnDatabase,
        deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder
    ): LifecycleObserver {
        return DeviceShieldNotificationScheduler(coroutineScope, workManager, vpnDatabase, deviceShieldAlertNotificationBuilder)
    }

    @Provides
    @IntoSet
    fun provideDeviceShieldNotificationWorkerInjectorPlugin(
        dailyNotificationPressedHandler: DailyNotificationPressedHandler,
        weeklyNotificationPressedHandler: WeeklyNotificationPressedHandler,
        deviceShieldPixels: DeviceShieldPixels,
        repository: AppTrackerBlockingStatsRepository,
        notificationManagerCompat: NotificationManagerCompat,
        deviceShieldNotificationFactory: DeviceShieldNotificationFactory,
        deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder,
        vpnDatabase: VpnDatabase
    ): WorkerInjectorPlugin {
        return DeviceShieldNotificationWorkerInjectorPlugin(
            dailyNotificationPressedHandler,
            weeklyNotificationPressedHandler,
            deviceShieldPixels,
            repository,
            notificationManagerCompat,
            deviceShieldNotificationFactory,
            deviceShieldAlertNotificationBuilder,
            vpnDatabase
        )
    }
}

class DeviceShieldNotificationScheduler(
    private val coroutineScope: CoroutineScope,
    private val workManager: WorkManager,
    private val vpnDatabase: VpnDatabase,
    private val deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun scheduleUsageNotification() {
        scheduleDailyNotification()
        scheduleWeeklyNotification()
    }

    private fun scheduleDailyNotification() {
        val vpnNotificationsDao = vpnDatabase.vpnNotificationsDao()
        coroutineScope.launch {
            val exists = withContext(Dispatchers.IO) {
                vpnNotificationsDao.exists(VPN_DAILY_NOTIFICATION_ID)
            }
            if (exists) {
                val timesRun = withContext(Dispatchers.IO) {
                    vpnNotificationsDao.get(VPN_DAILY_NOTIFICATION_ID).timesRun
                }
                if (timesRun > TOTAL_DAILY_NOTIFICATIONS) {
                    Timber.v("Vpn Daily notification has ran $timesRun times, we don't need to ran it anymore")

                    val workQuery = WorkQuery.Builder
                        .fromUniqueWorkNames(listOf(WORKER_VPN_DAILY_NOTIFICATION_NAME))
                        .build()

                    val workInfo = workManager.getWorkInfos(workQuery).get()
                    if (workInfo.isEmpty()) {
                        Timber.v("Vpn Daily notification has already been removed from WorkManager, nothing to do")
                    } else {
                        workManager.cancelUniqueWork(WORKER_VPN_DAILY_NOTIFICATION_NAME)
                        Timber.v("Vpn Daily notification has now been removed from WorkManager")
                    }
                } else {
                    Timber.v("Vpn Daily notification has ran $timesRun times out of $TOTAL_DAILY_NOTIFICATIONS")
                }
            } else {
                Timber.v("Scheduling the Vpn Daily notifications worker")
                val dailyNotificationRequest = PeriodicWorkRequestBuilder<DeviceShieldDailyNotificationWorker>(24, TimeUnit.HOURS)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .setInitialDelay(24, TimeUnit.HOURS)
                    .build()
                vpnNotificationsDao.increment(VPN_DAILY_NOTIFICATION_ID)
                workManager.enqueueUniquePeriodicWork(WORKER_VPN_DAILY_NOTIFICATION_NAME, ExistingPeriodicWorkPolicy.KEEP, dailyNotificationRequest)
            }
        }
    }

    private fun scheduleWeeklyNotification() {
        Timber.v("Scheduling the Vpn Weekly notifications worker")

        val weeklyNotificationRequest = PeriodicWorkRequestBuilder<DeviceShieldWeeklyNotificationWorker>(7, TimeUnit.DAYS)
            .addTag(WORKER_VPN_WEEKLY_NOTIFICATION_TAG)
            .setInitialDelay(FIRST_WEEKLY_NOTIFICATION_DELAY, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(WORKER_VPN_WEEKLY_NOTIFICATION_NAME, ExistingPeriodicWorkPolicy.KEEP, weeklyNotificationRequest)
    }

    class DeviceShieldDailyNotificationWorker(val context: Context, val params: WorkerParameters) : CoroutineWorker(context, params) {
        lateinit var notificationPressedHandler: DailyNotificationPressedHandler
        lateinit var deviceShieldPixels: DeviceShieldPixels
        lateinit var repository: AppTrackerBlockingStatsRepository
        lateinit var notificationManager: NotificationManagerCompat
        lateinit var deviceShieldNotificationFactory: DeviceShieldNotificationFactory
        lateinit var vpnNotificationsDao: VpnNotificationsDao
        lateinit var deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder

        override suspend fun doWork(): Result {
            Timber.v("Vpn Daily notification worker is now awake")

            if (vpnNotificationsDao.exists(VPN_DAILY_NOTIFICATION_ID)) {
                vpnNotificationsDao.increment(VPN_DAILY_NOTIFICATION_ID)
                val timesRun = vpnNotificationsDao.get(VPN_DAILY_NOTIFICATION_ID).timesRun
                if (timesRun >= TOTAL_DAILY_NOTIFICATIONS) {
                    Timber.v("Vpn Daily notification has ran $timesRun times out of $TOTAL_DAILY_NOTIFICATIONS, we don't need to ran it anymore")
                    return Result.success()
                } else {
                    Timber.v("Vpn Daily notification has ran $timesRun times out of $TOTAL_DAILY_NOTIFICATIONS")
                }
            } else {
                Timber.v("Vpn Daily notification running for the first time")
                vpnNotificationsDao.insert(VpnNotification(VPN_DAILY_NOTIFICATION_ID, 1))
            }

            showNotification()
            return Result.success()
        }

        private suspend fun showNotification() {
            val deviceShieldNotification = deviceShieldNotificationFactory.createDailyDeviceShieldNotification().also {
                notificationPressedHandler.notificationVariant = it.notificationVariant
            }

            if (!deviceShieldNotification.hidden) {
                val notification =
                    deviceShieldAlertNotificationBuilder.buildDeviceShieldNotification(context, deviceShieldNotification, notificationPressedHandler)
                deviceShieldPixels.didShowDailyNotification(deviceShieldNotification.notificationVariant)
                notificationManager.notify(VPN_DAILY_NOTIFICATION_ID, notification)
                Timber.v("Vpn Daily notification is now shown")
            } else {
                Timber.v("Vpn Daily notification won't be shown because there is no data to show")
            }
        }
    }

    class DeviceShieldWeeklyNotificationWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        lateinit var notificationPressedHandler: WeeklyNotificationPressedHandler
        lateinit var deviceShieldPixels: DeviceShieldPixels
        lateinit var notificationManager: NotificationManagerCompat
        lateinit var deviceShieldNotificationFactory: DeviceShieldNotificationFactory
        lateinit var deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder

        override suspend fun doWork(): Result {
            Timber.v("Vpn Weekly notification worker is now awake")

            val deviceShieldNotification = deviceShieldNotificationFactory.createWeeklyDeviceShieldNotification().also {
                notificationPressedHandler.notificationVariant = it.notificationVariant
            }

            if (!deviceShieldNotification.hidden) {
                Timber.v("Vpn Daily notification won't be shown because there is no data to show")
                val notification = deviceShieldAlertNotificationBuilder.buildDeviceShieldNotification(
                    context, deviceShieldNotification, notificationPressedHandler
                )
                deviceShieldPixels.didShowWeeklyNotification(deviceShieldNotification.notificationVariant)
                notificationManager.notify(VPN_WEEKLY_NOTIFICATION_ID, notification)
            }

            return Result.success()
        }
    }

    companion object {
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
    private var deviceShieldPixels: DeviceShieldPixels,
    private val repository: AppTrackerBlockingStatsRepository,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val deviceShieldNotificationFactory: DeviceShieldNotificationFactory,
    private val deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder,
    private val vpnDatabase: VpnDatabase
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is DeviceShieldNotificationScheduler.DeviceShieldDailyNotificationWorker) {
            worker.deviceShieldNotificationFactory = deviceShieldNotificationFactory
            worker.repository = repository
            worker.notificationManager = notificationManagerCompat
            worker.deviceShieldPixels = deviceShieldPixels
            worker.notificationPressedHandler = dailyNotificationPressedHandler
            worker.vpnNotificationsDao = vpnDatabase.vpnNotificationsDao()
            worker.deviceShieldAlertNotificationBuilder = deviceShieldAlertNotificationBuilder
            return true
        }

        if (worker is DeviceShieldNotificationScheduler.DeviceShieldWeeklyNotificationWorker) {
            worker.deviceShieldNotificationFactory = deviceShieldNotificationFactory
            worker.deviceShieldPixels = deviceShieldPixels
            worker.notificationPressedHandler = weeklyNotificationPressedHandler
            return true
        }

        return false
    }
}
