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

package com.duckduckgo.mobile.android.vpn.service.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.TaskStackBuilder
import com.duckduckgo.mobile.android.vpn.service.VpnReminderReceiver
import com.duckduckgo.mobile.android.vpn.service.VpnReminderReceiver.Companion.ACTION_VPN_REMINDER_RESTART
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity

internal fun getAppTPNotificationPressIntent(context: Context): PendingIntent? {
    return TaskStackBuilder.create(context).run {
        addNextIntentWithParentStack(DeviceShieldTrackerActivity.intent(context = context))
        getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

internal fun getAppTPStartIntent(context: Context): PendingIntent? {
    return Intent(context, VpnReminderReceiver::class.java).let { intent ->
        intent.action = ACTION_VPN_REMINDER_RESTART
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
