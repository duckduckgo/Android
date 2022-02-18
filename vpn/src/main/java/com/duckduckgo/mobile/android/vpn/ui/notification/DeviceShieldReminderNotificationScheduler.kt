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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationWorker
import com.duckduckgo.mobile.android.vpn.service.VpnReminderReceiver
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.model.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SingleInstanceIn(VpnScope::class)
@ContributesMultibinding(VpnScope::class)
class DeviceShieldReminderNotificationScheduler @Inject constructor(
    private val context: Context,
    private val workManager: WorkManager,
    private val notificationManager: NotificationManagerCompat,
    private val deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder
) : VpnServiceCallbacks {

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        scheduleUndesiredStopReminderAlarm()
        cancelReminderForTomorrow()
        hideReminderNotification()
        enableReminderReceiver()
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        when (vpnStopReason) {
            is VpnStopReason.SelfStop -> onVPNManuallyStopped()
            is VpnStopReason.Revoked -> onVPNRevoked()
            else -> onVPNUndesiredStop()
        }
    }

    private fun onVPNManuallyStopped() {
        showImmediateReminderNotification()
        cancelUndesiredStopReminderAlarm()
        scheduleReminderForTomorrow()
    }

    private fun onVPNRevoked() {
        showImmediateRevokedNotification()
        cancelUndesiredStopReminderAlarm()
        scheduleReminderForTomorrow()
    }

    private fun onVPNUndesiredStop() {
        scheduleUndesiredStopReminderAlarm()
    }

    private fun scheduleUndesiredStopReminderAlarm() {
        Timber.v("Scheduling the VpnReminderNotificationWorker worker 5 hours from now")
        val request = PeriodicWorkRequestBuilder<VpnReminderNotificationWorker>(5, TimeUnit.HOURS)
            .setInitialDelay(5, TimeUnit.HOURS)
            .addTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun cancelUndesiredStopReminderAlarm() {
        workManager.cancelAllWorkByTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
    }

    private fun cancelReminderForTomorrow() {
        workManager.cancelAllWorkByTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
    }

    private fun hideReminderNotification() {
        notificationManager.cancel(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID)
    }

    private fun showImmediateReminderNotification() {
        val notification = deviceShieldAlertNotificationBuilder.buildReminderNotification(context, true)
        notificationManager.notify(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID, notification)
    }

    private fun showImmediateRevokedNotification() {
        val notification = deviceShieldAlertNotificationBuilder.buildRevokedNotification(context)
        notificationManager.notify(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID, notification)
    }

    private fun scheduleReminderForTomorrow() {
        Timber.v("Scheduling the VpnReminderNotification worker for tomorrow")
        val request = OneTimeWorkRequestBuilder<VpnReminderNotificationWorker>()
            .setInitialDelay(24, TimeUnit.HOURS)
            .addTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
            .build()

        workManager.enqueueUniqueWork(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG, ExistingWorkPolicy.KEEP, request)
    }

    private fun enableReminderReceiver() {
        val receiver = ComponentName(context, VpnReminderReceiver::class.java)

        context.packageManager.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
