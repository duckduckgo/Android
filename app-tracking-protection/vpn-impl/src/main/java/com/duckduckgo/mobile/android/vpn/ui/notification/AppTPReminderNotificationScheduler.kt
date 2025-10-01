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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationContentPlugin
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationContentPlugin.Type.DISABLED
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationContentPlugin.Type.REVOKED
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationWorker
import com.duckduckgo.mobile.android.vpn.service.VpnReminderReceiver
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.notification.getHighestPriorityPluginForType
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class AppTPReminderNotificationScheduler @Inject constructor(
    private val context: Context,
    private val workManager: WorkManager,
    private val notificationManager: NotificationManagerCompat,
    private val vpnFeatureRemover: VpnFeatureRemover,
    private val dispatchers: DispatcherProvider,
    private val vpnReminderNotificationBuilder: VpnReminderNotificationBuilder,
    private val vpnReminderNotificationContentPluginPoint: PluginPoint<VpnReminderNotificationContentPlugin>,
    private val appTrackingProtection: AppTrackingProtection,
) : VpnServiceCallbacks {
    private var isAppTPEnabled: AtomicReference<Boolean> = AtomicReference(false)

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatchers.io()) {
            isAppTPEnabled.set(appTrackingProtection.isEnabled())
            if (isAppTPEnabled.get()) {
                // These are all relevant for when AppTP has been enabled.
                scheduleUndesiredStopReminderAlarm()
                cancelReminderForTomorrow()
                hideReminderNotification()
            }
            enableReminderReceiver()
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        coroutineScope.launch(dispatchers.io()) {
            when (vpnStopReason) {
                VpnStopReason.RESTART -> {} // no-op
                is VpnStopReason.SELF_STOP -> onVPNManuallyStopped(coroutineScope, vpnStopReason.snoozedTriggerAtMillis)
                VpnStopReason.REVOKED -> onVPNRevoked()
                else -> onVPNUndesiredStop()
            }
        }
    }

    private suspend fun onVPNManuallyStopped(coroutineScope: CoroutineScope, snoozedTriggerAtMillis: Long) {
        coroutineScope.launch(dispatchers.io()) {
            if (vpnFeatureRemover.isFeatureRemoved()) {
                logcat { "VPN Manually stopped because user disabled the feature, nothing to do" }
            } else {
                if (snoozedTriggerAtMillis == 0L) {
                    handleNotifForDisabledAppTP()
                }
            }
        }
    }

    private suspend fun shouldShowImmediateNotification(): Boolean {
        // When VPN is stopped and if AppTP has been prior enabled AND user has been onboarded, then we show the disabled notif
        return isAppTPEnabled.get() && appTrackingProtection.isOnboarded()
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatchers.io()) {
            val reconfiguredAppTPState = appTrackingProtection.isEnabled()
            if (isAppTPEnabled.get() != reconfiguredAppTPState) {
                if (!reconfiguredAppTPState) {
                    // AppTP changed state to disabled
                    logcat { "VPN has been reconfigured, showing disabled notification for AppTP" }
                    handleNotifForDisabledAppTP()
                } else {
                    // AppTP changed state to enabled
                    scheduleUndesiredStopReminderAlarm()
                    cancelReminderForTomorrow()
                    hideReminderNotification()
                    isAppTPEnabled.set(true)
                }
            }
        }
    }

    private suspend fun handleNotifForDisabledAppTP() {
        if (shouldShowImmediateNotification()) {
            logcat { "VPN Manually stopped, showing disabled notification for AppTP" }
            showImmediateReminderNotification()
            cancelUndesiredStopReminderAlarm()
            scheduleReminderForTomorrow()
            isAppTPEnabled.set(false)
        }
    }

    private suspend fun onVPNRevoked() {
        if (shouldShowImmediateNotification()) {
            // These only need to be executed when AppTP has been enabled when vpn was revoked
            // else either AppTP was never enabled OR AppTP was disabled before hence already schedule reminders
            logcat { "VPN has been revoked, showing revoked notification for AppTP" }
            showImmediateRevokedNotification()
            cancelUndesiredStopReminderAlarm()
            scheduleReminderForTomorrow()
            isAppTPEnabled.set(false)
        }
    }

    private fun onVPNUndesiredStop() {
        scheduleUndesiredStopReminderAlarm()
    }

    private fun scheduleUndesiredStopReminderAlarm() {
        logcat { "Scheduling the VpnReminderNotificationWorker worker 5 hours from now" }
        val request = PeriodicWorkRequestBuilder<VpnReminderNotificationWorker>(5, TimeUnit.HOURS)
            .setInitialDelay(5, TimeUnit.HOURS)
            .addTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
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
        vpnReminderNotificationContentPluginPoint.getHighestPriorityPluginForType(DISABLED)?.let {
            it.getContent()?.let { content ->
                logcat { "Showing disabled notification from $it" }
                notificationManager.checkPermissionAndNotify(
                    context,
                    TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID,
                    vpnReminderNotificationBuilder.buildReminderNotification(content),
                )
            }
        }
    }

    private fun showImmediateRevokedNotification() {
        vpnReminderNotificationContentPluginPoint.getHighestPriorityPluginForType(REVOKED)?.let {
            it.getContent()?.let { content ->
                logcat { "Showing revoked notification from $it" }
                notificationManager.checkPermissionAndNotify(
                    context,
                    TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID,
                    vpnReminderNotificationBuilder.buildReminderNotification(content),
                )
            }
        }
    }

    private fun scheduleReminderForTomorrow() {
        logcat { "Scheduling the VpnReminderNotification worker for tomorrow" }
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
            PackageManager.DONT_KILL_APP,
        )
    }
}
