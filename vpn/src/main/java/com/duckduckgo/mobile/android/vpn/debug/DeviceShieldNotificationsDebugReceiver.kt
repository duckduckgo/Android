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

package com.duckduckgo.mobile.android.vpn.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.BuildConfig
import com.duckduckgo.mobile.android.vpn.ui.notification.*
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * This receiver allows to trigger appTP notifications, to do so, in the command line:
 *
 * $ adb shell am broadcast -a notify --es <weekly/daily> <N>
 *
 * where `--es weekly <N>` will trigger the N'th variant of the weekly notification
 * where `--es daily <N>` will trigger the N'th variant of the daily notification
 */
class DeviceShieldNotificationsDebugReceiver(
    context: Context,
    intentAction: String = "notify",
    private val receiver: (Intent) -> Unit
) : BroadcastReceiver(), LifecycleObserver {

    init {
        context.registerReceiver(this, IntentFilter(intentAction))
    }

    override fun onReceive(context: Context, intent: Intent) {
        receiver(intent)
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class DeviceShieldNotificationsDebugReceiverRegister @Inject constructor(
    private val context: Context,
    private val deviceShieldNotificationFactory: DeviceShieldNotificationFactory,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val weeklyNotificationPressedHandler: WeeklyNotificationPressedHandler,
    private val dailyNotificationPressedHandler: DailyNotificationPressedHandler
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun register() {
        if (!BuildConfig.DEBUG) {
            Timber.i("Will not register DeviceShieldNotificationsDebugReceiver, not in DEBUG mode")
            return
        }

        Timber.i("Debug receiver DeviceShieldNotificationsDebugReceiver registered")

        DeviceShieldNotificationsDebugReceiver(context) { intent ->
            val weekly = kotlin.runCatching { intent.getStringExtra("weekly")?.toInt() }.getOrNull()
            val daily = kotlin.runCatching { intent.getStringExtra("daily")?.toInt() }.getOrNull()

            GlobalScope.launch(Dispatchers.IO) {
                val notification = if (weekly != null) {
                    Timber.v("Debug - Sending weekly notification $weekly")
                    weeklyNotificationPressedHandler.notificationVariant = weekly
                    val deviceShieldNotification = deviceShieldNotificationFactory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(weekly)

                    DeviceShieldAlertNotificationBuilder.buildDeviceShieldNotification(
                        context, deviceShieldNotification, weeklyNotificationPressedHandler
                    )
                } else if (daily != null) {
                    Timber.v("Debug - Sending daily notification $daily")
                    dailyNotificationPressedHandler.notificationVariant = daily
                    val deviceShieldNotification = deviceShieldNotificationFactory.dailyNotificationFactory.createDailyDeviceShieldNotification(daily)

                    DeviceShieldAlertNotificationBuilder.buildDeviceShieldNotification(
                        context, deviceShieldNotification, dailyNotificationPressedHandler
                    )
                } else {
                    Timber.v("Debug - invalid notification type")
                    null
                }

                notification?.let {
                    notificationManagerCompat.notify(DeviceShieldNotificationScheduler.VPN_WEEKLY_NOTIFICATION_ID, it)
                }
            }
        }
    }
}
