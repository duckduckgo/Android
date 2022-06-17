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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.dao.VpnNotification
import com.duckduckgo.mobile.android.vpn.dao.VpnNotificationsDao
import com.duckduckgo.mobile.android.vpn.di.VpnCoroutineScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationScheduler.Companion
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Module
@ContributesTo(AppScope::class)
object DeviceShieldNotificationSchedulerModule {
    @Provides
    @IntoSet
    fun provideDeviceShieldNotificationScheduler(
        @VpnCoroutineScope coroutineScope: CoroutineScope,
        workManager: WorkManager,
        vpnDatabase: VpnDatabase
    ): LifecycleObserver {
        return DeviceShieldNotificationScheduler(coroutineScope, workManager, vpnDatabase)
    }

    @Provides
    fun provideVpnNotificationsDao(vpnDatabase: VpnDatabase): VpnNotificationsDao = vpnDatabase.vpnNotificationsDao()
}

class DeviceShieldNotificationScheduler(
    private val coroutineScope: CoroutineScope,
    private val workManager: WorkManager,
    private val vpnDatabase: VpnDatabase
) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
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

@ContributesWorker(AppScope::class)
class DeviceShieldDailyNotificationWorker(
    val context: Context,
    val params: WorkerParameters
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var notificationPressedHandler: DailyNotificationPressedHandler
    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels
    @Inject
    lateinit var repository: AppTrackerBlockingStatsRepository
    @Inject
    lateinit var notificationManager: NotificationManagerCompat
    @Inject
    lateinit var deviceShieldNotificationFactory: DeviceShieldNotificationFactory
    @Inject
    lateinit var vpnNotificationsDao: VpnNotificationsDao
    @Inject
    lateinit var deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder

    override suspend fun doWork(): Result {
        Timber.v("Vpn Daily notification worker is now awake")

        if (vpnNotificationsDao.exists(DeviceShieldNotificationScheduler.VPN_DAILY_NOTIFICATION_ID)) {
            vpnNotificationsDao.increment(DeviceShieldNotificationScheduler.VPN_DAILY_NOTIFICATION_ID)
            val timesRun = vpnNotificationsDao.get(DeviceShieldNotificationScheduler.VPN_DAILY_NOTIFICATION_ID).timesRun
            if (timesRun >= DeviceShieldNotificationScheduler.TOTAL_DAILY_NOTIFICATIONS) {
                Timber.v(
                    "Vpn Daily notification has ran $timesRun times out of" +
                        " ${DeviceShieldNotificationScheduler.TOTAL_DAILY_NOTIFICATIONS}, we don't need to ran it anymore"
                )
                return Result.success()
            } else {
                Timber.v("Vpn Daily notification has ran $timesRun times out of ${DeviceShieldNotificationScheduler.TOTAL_DAILY_NOTIFICATIONS}")
            }
        } else {
            Timber.v("Vpn Daily notification running for the first time")
            vpnNotificationsDao.insert(VpnNotification(DeviceShieldNotificationScheduler.VPN_DAILY_NOTIFICATION_ID, 1))
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
                deviceShieldAlertNotificationBuilder.buildStatusNotification(context, deviceShieldNotification, notificationPressedHandler)
            deviceShieldPixels.didShowDailyNotification(deviceShieldNotification.notificationVariant)
            notificationManager.notify(DeviceShieldNotificationScheduler.VPN_DAILY_NOTIFICATION_ID, notification)
            Timber.v("Vpn Daily notification is now shown")
        } else {
            Timber.v("Vpn Daily notification won't be shown because there is no data to show")
        }
    }
}

@ContributesWorker(AppScope::class)
class DeviceShieldWeeklyNotificationWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var notificationPressedHandler: WeeklyNotificationPressedHandler
    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels
    @Inject
    lateinit var deviceShieldNotificationFactory: DeviceShieldNotificationFactory
    @Inject
    lateinit var notificationManager: NotificationManagerCompat
    @Inject
    lateinit var deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder

    override suspend fun doWork(): Result {
        Timber.v("Vpn Weekly notification worker is now awake")

        val deviceShieldNotification = deviceShieldNotificationFactory.createWeeklyDeviceShieldNotification().also {
            notificationPressedHandler.notificationVariant = it.notificationVariant
        }

        if (!deviceShieldNotification.hidden) {
            Timber.v("Vpn Daily notification won't be shown because there is no data to show")
            val notification = deviceShieldAlertNotificationBuilder.buildStatusNotification(
                context, deviceShieldNotification, notificationPressedHandler
            )
            deviceShieldPixels.didShowWeeklyNotification(deviceShieldNotification.notificationVariant)
            notificationManager.notify(Companion.VPN_WEEKLY_NOTIFICATION_ID, notification)
        }

        return Result.success()
    }
}
