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

package com.duckduckgo.mobile.android.vpn.service

import android.app.PendingIntent
import androidx.core.app.NotificationCompat

interface VpnReminderNotificationContentPlugin {
    /**
     * This method could be called to get the plugin's corresponding NotificationContent
     *
     * @return shall return the content of the notification or null if the plugin does not want to show content in the notification.
     */
    fun getContent(): NotificationContent?

    /**
     * The VPN will call this method to select what plugin will be displayed in the notification.
     * To select a proper priority:
     * - check the priority of any other plugins
     * - check with product/design what should be the priority of your plugin w.r.t. other plugins
     *
     * @return shall return the priority of the plugin.
     */
    fun getPriority(): NotificationPriority

    /**
     * This method will be called to select the specific Type of reminder notification that vpn wants to show.
     *
     * @return shall return the type of the reminder notification plugin.
     */
    fun getType(): Type

    data class NotificationContent(
        val isSilent: Boolean,
        val shouldAutoCancel: Boolean?,
        val title: String,
        val onNotificationPressIntent: PendingIntent?,
        val notificationAction: List<NotificationCompat.Action>,
    ) {
        companion object {
            val EMPTY = NotificationContent(
                isSilent = false,
                shouldAutoCancel = null,
                title = "",
                onNotificationPressIntent = null,
                notificationAction = emptyList(),
            )
        }
    }

    enum class Type {
        DISABLED,
        REVOKED,
    }

    enum class NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        VERY_HIGH,
    }
}
