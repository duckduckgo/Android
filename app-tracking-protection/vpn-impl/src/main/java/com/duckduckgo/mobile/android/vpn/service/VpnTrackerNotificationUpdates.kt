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
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldEnabledNotificationBuilder
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationFactory
import com.duckduckgo.mobile.android.vpn.ui.notification.OngoingNotificationPressedHandler
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@ContributesMultibinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class VpnTrackerNotificationUpdates @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val notificationManager: NotificationManagerCompat,
    private val ongoingNotificationPressedHandler: OngoingNotificationPressedHandler,
    private val vpnEnabledNotificationContentPluginPoint: PluginPoint<VpnEnabledNotificationContentPlugin>,
) : VpnServiceCallbacks {

    private val job = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            vpnEnabledNotificationContentPluginPoint.getHighestPriorityPlugin().getUpdatedContent().collectLatest { content ->
                val deviceShieldNotification = content?.let {
                    DeviceShieldNotificationFactory.DeviceShieldNotification.from(it)
                } ?: DeviceShieldNotificationFactory.DeviceShieldNotification()

                updateNotification(deviceShieldNotification)
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        job.cancel()
    }

    private fun updateNotification(deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification) {
        val notification = DeviceShieldEnabledNotificationBuilder
            .buildTrackersBlockedNotification(context, deviceShieldNotification, ongoingNotificationPressedHandler)
        notificationManager.notify(TrackerBlockingVpnService.VPN_FOREGROUND_SERVICE_ID, notification)
    }
}
