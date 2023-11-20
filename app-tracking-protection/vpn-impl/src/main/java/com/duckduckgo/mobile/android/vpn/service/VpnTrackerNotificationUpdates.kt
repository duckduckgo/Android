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

package com.duckduckgo.mobile.android.vpn.service

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.ui.notification.VpnEnabledNotificationBuilder
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import logcat.logcat

@ContributesMultibinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class VpnTrackerNotificationUpdates @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val notificationManager: NotificationManagerCompat,
    private val vpnEnabledNotificationContentPluginPoint: PluginPoint<VpnEnabledNotificationContentPlugin>,
) : VpnServiceCallbacks {

    private val job = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            val notificationContentFlow = vpnEnabledNotificationContentPluginPoint.getHighestPriorityPlugin()?.getUpdatedContent()
            notificationContentFlow?.let {
                it.collectLatest { content ->
                    val vpnNotification = content ?: VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent.EMPTY

                    updateNotification(vpnNotification)
                }
            }
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        logcat { "Notification updates: onVpnReconfigured" }
        onVpnStarted(coroutineScope)
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        job.cancel()
    }

    private fun updateNotification(
        vpnNotification: VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent,
    ) {
        val notification = VpnEnabledNotificationBuilder.buildVpnEnabledUpdateNotification(context, vpnNotification)
        notificationManager.notify(TrackerBlockingVpnService.VPN_FOREGROUND_SERVICE_ID, notification)
    }
}
