/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.notification

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class NetPDisabledNotificationScheduler @Inject constructor(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val netPDisabledNotificationBuilder: NetPDisabledNotificationBuilder,
    private val networkProtectionState: NetworkProtectionState,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : VpnServiceCallbacks {

    private var isNetPEnabled: AtomicReference<Boolean> = AtomicReference(false)

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            isNetPEnabled.set(networkProtectionState.isEnabled())
            if (isNetPEnabled.get()) {
                cancelDisabledNotification()
            }
            enableReminderReceiver()
        }
    }

    private fun cancelDisabledNotification() {
        notificationManager.cancel(NETP_REMINDER_NOTIFICATION_ID)
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        coroutineScope.launch(dispatcherProvider.io()) {
            when (vpnStopReason) {
                VpnStopReason.RESTART -> {} // no-op
                is VpnStopReason.SELF_STOP -> onVPNManuallyStopped(vpnStopReason.snoozedTriggerAtMillis)
                VpnStopReason.REVOKED -> onVPNRevoked()
                else -> {}
            }
        }
    }

    private suspend fun shouldShowImmediateNotification(): Boolean {
        // When VPN is stopped and if AppTP has been enabled AND user has been onboarded, then we show the disabled notif
        return isNetPEnabled.get() && networkProtectionState.isOnboarded()
    }

    private suspend fun onVPNManuallyStopped(snoozedTriggerAtMillis: Long) {
        if (shouldShowImmediateNotification()) {
            logcat { "VPN Manually stopped, showing disabled notification for NetP" }
            showSnoozedOrDisabledNotification(snoozedTriggerAtMillis)
            isNetPEnabled.set(false)
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            val reconfiguredNetPState = networkProtectionState.isEnabled()
            if (isNetPEnabled.getAndSet(reconfiguredNetPState) != reconfiguredNetPState) {
                if (!reconfiguredNetPState) {
                    logcat { "VPN has been reconfigured, showing disabled notification for NetP" }
                    showDisabledNotification()
                } else {
                    cancelDisabledNotification()
                }
            }
        }
    }

    private fun showSnoozedOrDisabledNotification(triggerAtMillis: Long) {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (triggerAtMillis != 0L) {
                if (!netPSettingsLocalConfig.vpnNotificationAlerts().isEnabled()) return@launch
                notificationManager.checkPermissionAndNotify(
                    context,
                    NETP_REMINDER_NOTIFICATION_ID,
                    netPDisabledNotificationBuilder.buildSnoozeNotification(context, triggerAtMillis),
                )
            } else {
                showDisabledNotification()
            }
        }
    }

    private fun showDisabledNotification() {
        coroutineScope.launch(dispatcherProvider.io()) {
            logcat { "Showing disabled notification for NetP" }
            if (!netPSettingsLocalConfig.vpnNotificationAlerts().isEnabled()) return@launch
            netPDisabledNotificationBuilder.buildDisabledNotification(context)?.let { notification ->
                notificationManager.checkPermissionAndNotify(
                    context,
                    NETP_REMINDER_NOTIFICATION_ID,
                    notification,
                )
            }
        }
    }

    private suspend fun onVPNRevoked() {
        if (shouldShowImmediateNotification()) {
            logcat { "VPN has been revoked, showing revoked notification for NetP" }
            showRevokedNotification()
            isNetPEnabled.set(false)
        }
    }

    private fun showRevokedNotification() {
        coroutineScope.launch(dispatcherProvider.io()) {
            logcat { "Showing disabled by vpn notification for NetP" }
            if (!netPSettingsLocalConfig.vpnNotificationAlerts().isEnabled()) return@launch
            notificationManager.checkPermissionAndNotify(
                context,
                NETP_REMINDER_NOTIFICATION_ID,
                netPDisabledNotificationBuilder.buildDisabledByVpnNotification(context),
            )
        }
    }

    private fun enableReminderReceiver() {
        val receiver = ComponentName(context, NetPEnableReceiver::class.java)

        context.packageManager.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    companion object {
        const val NETP_REMINDER_NOTIFICATION_ID = 444
    }
}
