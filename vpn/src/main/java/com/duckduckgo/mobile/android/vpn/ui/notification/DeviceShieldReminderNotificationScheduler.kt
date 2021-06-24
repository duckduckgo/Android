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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationWorker
import com.duckduckgo.mobile.android.vpn.service.VpnReminderReceiver
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DeviceShieldReminderNotificationScheduler @Inject constructor(
    private val context: Context,
    private val workManager: WorkManager,
    private val notificationManager: NotificationManagerCompat
) {

    fun onVPNStarted() {
        scheduleUndesiredStopReminderAlarm()
        cancelReminderForTomorrow()
        hideReminderNotification()
    }

    fun onVPNManuallyStopped() {
        showImmediateReminderNotification()
        cancelUndesiredStopReminderAlarm()
        scheduleReminderForTomorrow()
    }

    fun onVPNUndesiredStop() {
        scheduleUndesiredStopReminderAlarm()
    }

    private fun scheduleUndesiredStopReminderAlarm() {
        Timber.v("Scheduling the VpnReminderNotificationWorker worker 5 hours from now")
        val request = PeriodicWorkRequestBuilder<VpnReminderNotificationWorker>(5, TimeUnit.HOURS)
            .setInitialDelay(5, TimeUnit.HOURS)
            .addTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG, ExistingPeriodicWorkPolicy.KEEP, request)

        val receiver = ComponentName(context, VpnReminderReceiver::class.java)

        context.packageManager.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

    }

    private fun cancelUndesiredStopReminderAlarm() {
        workManager.cancelAllWorkByTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)

        // this alarm is only intended for the cases where the VPN turned itself off and not because the user wanted to
        val receiver = ComponentName(context, VpnReminderReceiver::class.java)

        context.packageManager.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun cancelReminderForTomorrow() {
        workManager.cancelAllWorkByTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
    }

    private fun hideReminderNotification() {
        notificationManager.cancel(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID)
    }

    private fun showImmediateReminderNotification() {
        val notification = DeviceShieldAlertNotificationBuilder.buildReminderNotification(context, false)
        notificationManager.notify(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID, notification)
    }

    private fun scheduleReminderForTomorrow() {
        Timber.v("Scheduling the VpnReminderNotification worker a week from now")
        val request = OneTimeWorkRequestBuilder<VpnReminderNotificationWorker>()
            .setInitialDelay(24, TimeUnit.HOURS)
            .addTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
            .build()

        workManager.enqueueUniqueWork(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG, ExistingWorkPolicy.KEEP, request)
    }

}
