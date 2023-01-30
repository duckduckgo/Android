/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.alwayson

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.text.SpannableStringBuilder
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.dao.VpnServiceStateStatsDao
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldAlertNotificationBuilder
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationFactory
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
@SingleInstanceIn(VpnScope::class)
class AlwaysOnLockDownDetector @Inject constructor(
    private val vpnServiceStateStatsDao: VpnServiceStateStatsDao,
    private val dispatcherProvider: DispatcherProvider,
    private val deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder,
    private val context: Context,
    private val notificationManagerCompat: NotificationManagerCompat,
) : VpnServiceCallbacks {

    private val job = ConflatedJob()
    private val resources = context.resources
    private val notificationId = Random.nextInt()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            vpnServiceStateStatsDao.getStateStats()
                .mapNotNull { it?.alwaysOnState?.alwaysOnLockedDown }
                .distinctUntilChanged()
                .cancellable()
                .collect { alwaysOnLockedDown ->
                    if (alwaysOnLockedDown) {
                        showNotification()
                    } else {
                        removeNotification()
                    }
                }
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        job.cancel()
        removeNotification()
    }

    private fun showNotification() {
        val title = SpannableStringBuilder(resources.getString(R.string.atp_AlwaysOnLockDownNotificationTitle))
        val notification = DeviceShieldNotificationFactory.DeviceShieldNotification(title = title)
        deviceShieldAlertNotificationBuilder.buildAlwaysOnLockdownNotification(
            context,
            notification,
            NotificationPressedHandler(),
        ).also {
            notificationManagerCompat.notify(notificationId, it)
        }
    }

    private fun removeNotification() {
        notificationManagerCompat.cancel(notificationId)
    }

    private class NotificationPressedHandler constructor() : ResultReceiver(Handler(Looper.getMainLooper())) {
        override fun onReceiveResult(
            resultCode: Int,
            resultData: Bundle?,
        ) {
            logcat { "Lockdown notification pressed" }
        }
    }
}
