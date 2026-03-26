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

import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.cohort.CohortStore
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationScheduler.Companion
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationScheduler.Companion.VPN_DAILY_NOTIFICATION_ID
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.*
import logcat.logcat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DeviceShieldNotificationScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val cohortStore: CohortStore,
    private val dispatchers: DispatcherProvider,
) : VpnServiceCallbacks {

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatchers.io()) {
            scheduleDailyNotification()
            scheduleWeeklyNotification()
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        // noop
    }

    private suspend fun scheduleDailyNotification() = withContext(dispatchers.io()) {
        val daysRun = cohortStore.getCohortStoredLocalDate()?.daysUntilNow() ?: return@withContext

        if (daysRun > TOTAL_DAILY_NOTIFICATIONS) {
            logcat { "Vpn Daily notification has ran $daysRun times, we don't need to ran it anymore" }

            val workQuery = WorkQuery.Builder
                .fromUniqueWorkNames(listOf(WORKER_VPN_DAILY_NOTIFICATION_NAME))
                .build()

            val workInfo = workManager.getWorkInfos(workQuery).get()
            if (workInfo.isEmpty()) {
                logcat { "Vpn Daily notification has already been removed from WorkManager, nothing to do" }
            } else {
                workManager.cancelUniqueWork(WORKER_VPN_DAILY_NOTIFICATION_NAME)
                logcat { "Vpn Daily notification has now been removed from WorkManager" }
            }
        } else {
            logcat { "Scheduling the Vpn Daily notifications worker" }
            val dailyNotificationRequest = PeriodicWorkRequestBuilder<DeviceShieldDailyNotificationWorker>(24, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .setInitialDelay(24, TimeUnit.HOURS)
                .addTag(WORKER_VPN_DAILY_NOTIFICATION_NAME)
                .build()
            workManager.enqueueUniquePeriodicWork(WORKER_VPN_DAILY_NOTIFICATION_NAME, ExistingPeriodicWorkPolicy.KEEP, dailyNotificationRequest)
        }
    }

    private fun scheduleWeeklyNotification() {
        logcat { "Scheduling the Vpn Weekly notifications worker" }

        val weeklyNotificationRequest = PeriodicWorkRequestBuilder<DeviceShieldWeeklyNotificationWorker>(7, TimeUnit.DAYS)
            .addTag(WORKER_VPN_WEEKLY_NOTIFICATION_TAG)
            .setInitialDelay(FIRST_WEEKLY_NOTIFICATION_DELAY, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(WORKER_VPN_WEEKLY_NOTIFICATION_NAME, ExistingPeriodicWorkPolicy.KEEP, weeklyNotificationRequest)
    }

    companion object {
        internal const val WORKER_VPN_DAILY_NOTIFICATION_NAME = "VpnDailyNotification"
        internal const val WORKER_VPN_WEEKLY_NOTIFICATION_TAG = "VpnWeeklyNotificationWorker"
        private const val WORKER_VPN_WEEKLY_NOTIFICATION_NAME = "VpnWeeklyNotification"
        private const val FIRST_WEEKLY_NOTIFICATION_DELAY = 14L

        internal const val VPN_DAILY_NOTIFICATION_ID = 998
        internal const val VPN_WEEKLY_NOTIFICATION_ID = 997
    }
}

private fun LocalDate.daysUntilNow(): Long {
    return ChronoUnit.DAYS.between(this, LocalDate.now())
}

@ContributesWorker(AppScope::class)
class DeviceShieldDailyNotificationWorker(
    val context: Context,
    val params: WorkerParameters,
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
    lateinit var deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder

    @Inject
    lateinit var cohortStore: CohortStore

    override suspend fun doWork(): Result {
        logcat { "Vpn Daily notification worker is now awake" }

        val daysRun = cohortStore.getCohortStoredLocalDate()?.daysUntilNow() ?: return Result.success()

        if (daysRun >= TOTAL_DAILY_NOTIFICATIONS) {
            logcat { "Vpn Daily notification has ran more than $TOTAL_DAILY_NOTIFICATIONS, we don't need to ran it anymore" }
        } else {
            logcat { "Vpn Daily notification has ran $daysRun times out of $TOTAL_DAILY_NOTIFICATIONS" }
            showNotification()
        }

        return Result.success()
    }

    private suspend fun showNotification() {
        if (ActivityCompat.checkSelfPermission(context, permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val deviceShieldNotification = deviceShieldNotificationFactory.createDailyDeviceShieldNotification().also {
                notificationPressedHandler.notificationVariant = it.notificationVariant
            }

            if (!deviceShieldNotification.hidden) {
                val notification =
                    deviceShieldAlertNotificationBuilder.buildStatusNotification(context, deviceShieldNotification, notificationPressedHandler)
                deviceShieldPixels.didShowDailyNotification(deviceShieldNotification.notificationVariant)
                notificationManager.notify(VPN_DAILY_NOTIFICATION_ID, notification)
                logcat { "Vpn Daily notification is now shown" }
            } else {
                logcat { "Vpn Daily notification won't be shown because there is no data to show" }
            }
        }
    }
}

@ContributesWorker(AppScope::class)
class DeviceShieldWeeklyNotificationWorker(
    val context: Context,
    params: WorkerParameters,
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
        logcat { "Vpn Weekly notification worker is now awake" }
        if (ActivityCompat.checkSelfPermission(context, permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val deviceShieldNotification = deviceShieldNotificationFactory.createWeeklyDeviceShieldNotification().also {
                notificationPressedHandler.notificationVariant = it.notificationVariant
            }

            if (!deviceShieldNotification.hidden) {
                logcat { "Vpn Daily notification won't be shown because there is no data to show" }
                val notification = deviceShieldAlertNotificationBuilder.buildStatusNotification(
                    context,
                    deviceShieldNotification,
                    notificationPressedHandler,
                )
                deviceShieldPixels.didShowWeeklyNotification(deviceShieldNotification.notificationVariant)
                notificationManager.notify(Companion.VPN_WEEKLY_NOTIFICATION_ID, notification)
            }
        }

        return Result.success()
    }
}

private const val TOTAL_DAILY_NOTIFICATIONS = 7L
