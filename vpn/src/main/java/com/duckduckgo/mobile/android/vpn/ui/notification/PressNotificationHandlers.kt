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

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import javax.inject.Inject
import timber.log.Timber

private const val INVALID_NOTIFICATION_VARIANT = -1

class ReminderNotificationPressedHandler
@Inject
constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
) : ResultReceiver(Handler(Looper.getMainLooper())) {
    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        Timber.v("Reminder notification pressed")
        deviceShieldPixels.didPressReminderNotification()
    }
}

class OngoingNotificationPressedHandler
@Inject
constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
) : ResultReceiver(Handler(Looper.getMainLooper())) {
    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        Timber.v("Ongoing notification pressed")
        deviceShieldPixels.didPressOngoingNotification()
    }
}

class WeeklyNotificationPressedHandler
@Inject
constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
) : ResultReceiver(Handler(Looper.getMainLooper())) {

    var notificationVariant: Int = INVALID_NOTIFICATION_VARIANT

    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        Timber.v("Weekly notification pressed")
        if (notificationVariant == INVALID_NOTIFICATION_VARIANT) {
            Timber.e("Weekly notification pressed reported with uninitialized notification variant")
        } else {
            deviceShieldPixels.didPressOnWeeklyNotification(notificationVariant)
        }
    }
}

class DailyNotificationPressedHandler
@Inject
constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
) : ResultReceiver(Handler(Looper.getMainLooper())) {

    var notificationVariant: Int = INVALID_NOTIFICATION_VARIANT

    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        Timber.v("Daily notification pressed")
        if (notificationVariant == INVALID_NOTIFICATION_VARIANT) {
            Timber.e("Daily notification pressed reported with uninitialized notification variant")
        } else {
            deviceShieldPixels.didPressOnDailyNotification(notificationVariant)
        }
    }
}
