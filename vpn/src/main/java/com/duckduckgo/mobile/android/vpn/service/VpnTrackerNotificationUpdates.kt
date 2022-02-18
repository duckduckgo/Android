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
import com.duckduckgo.app.global.formatters.time.model.dateOfLastHour
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldEnabledNotificationBuilder
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationFactory
import com.duckduckgo.mobile.android.vpn.ui.notification.OngoingNotificationPressedHandler
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class VpnTrackerNotificationUpdates @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val repository: AppTrackerBlockingStatsRepository,
    private val deviceShieldNotificationFactory: DeviceShieldNotificationFactory,
    private val notificationManager: NotificationManagerCompat,
    private val ongoingNotificationPressedHandler: OngoingNotificationPressedHandler
) : VpnServiceCallbacks {

    private var notificationTickerChannel = MutableStateFlow(System.currentTimeMillis())
    private val notificationTickerJob = ConflatedJob()
    private val newTrackerObserverJob = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        newTrackerObserverJob += coroutineScope.launch(dispatcherProvider.io()) {
            repository.getVpnTrackers({ dateOfLastHour() })
                .combine(notificationTickerChannel.asStateFlow()) { trackers, _ -> trackers }
                .onStart { startNewTrackerNotificationRefreshTicker(this@launch) }
                .collectLatest {
                    updateNotificationForNewTrackerFound(it)
                }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        notificationTickerJob.cancel()
        newTrackerObserverJob.cancel()
    }

    private fun updateNotificationForNewTrackerFound(trackersBlocked: List<VpnTracker>) {
        if (trackersBlocked.isNotEmpty()) {
            val deviceShieldNotification = deviceShieldNotificationFactory.createNotificationNewTrackerFound(trackersBlocked)
            val notification = DeviceShieldEnabledNotificationBuilder
                .buildTrackersBlockedNotification(context, deviceShieldNotification, ongoingNotificationPressedHandler)
            notificationManager.notify(TrackerBlockingVpnService.VPN_FOREGROUND_SERVICE_ID, notification)
        }
    }

    private fun startNewTrackerNotificationRefreshTicker(coroutineScope: CoroutineScope) {
        // this ticker ensures the ongoing notification information is not stale if we haven't
        // blocked trackers for a while
        notificationTickerJob += coroutineScope.launch {
            while (isActive) {
                notificationTickerChannel.emit(System.currentTimeMillis())
                delay(TimeUnit.HOURS.toMillis(1))
            }
        }
    }
}
