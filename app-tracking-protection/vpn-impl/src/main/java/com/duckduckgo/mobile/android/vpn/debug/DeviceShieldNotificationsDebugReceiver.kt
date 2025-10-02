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
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.registerNotExportedReceiver
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.ui.notification.*
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
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
    private val receiver: (Intent) -> Unit,
) : BroadcastReceiver() {

    init {
        context.registerNotExportedReceiver(this, IntentFilter(intentAction))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        receiver(intent)
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class DeviceShieldNotificationsDebugReceiverRegister @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val deviceShieldNotificationFactory: DeviceShieldNotificationFactory,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val weeklyNotificationPressedHandler: WeeklyNotificationPressedHandler,
    private val dailyNotificationPressedHandler: DailyNotificationPressedHandler,
    private val deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        if (!appBuildConfig.isDebug) {
            logcat { "Will not register DeviceShieldNotificationsDebugReceiver, not in DEBUG mode" }
            return
        }

        logcat { "Debug receiver DeviceShieldNotificationsDebugReceiver registered" }

        DeviceShieldNotificationsDebugReceiver(context) { intent ->
            val weekly = kotlin.runCatching { intent.getStringExtra("weekly")?.toInt() }.getOrNull()
            val daily = kotlin.runCatching { intent.getStringExtra("daily")?.toInt() }.getOrNull()

            coroutineScope.launch(dispatchers.io()) {
                val notification = if (weekly != null) {
                    logcat { "Debug - Sending weekly notification $weekly" }
                    weeklyNotificationPressedHandler.notificationVariant = weekly
                    val deviceShieldNotification =
                        deviceShieldNotificationFactory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(weekly)

                    deviceShieldAlertNotificationBuilder.buildStatusNotification(
                        context,
                        deviceShieldNotification,
                        weeklyNotificationPressedHandler,
                    )
                } else if (daily != null) {
                    logcat { "Debug - Sending daily notification $daily" }
                    dailyNotificationPressedHandler.notificationVariant = daily
                    val deviceShieldNotification = deviceShieldNotificationFactory.dailyNotificationFactory.createDailyDeviceShieldNotification(daily)

                    deviceShieldAlertNotificationBuilder.buildStatusNotification(
                        context,
                        deviceShieldNotification,
                        dailyNotificationPressedHandler,
                    )
                } else {
                    logcat { "Debug - invalid notification type" }
                    null
                }

                notification?.let {
                    notificationManagerCompat.checkPermissionAndNotify(context, DeviceShieldNotificationScheduler.VPN_WEEKLY_NOTIFICATION_ID, it)
                }
            }
        }
    }
}
