/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.os.Build.VERSION_CODES.O
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.duckduckgo.app.browser.R


class NotificationGenerator(val context: Context) {

    fun buildNotification(manager: NotificationManager, specification: NotificationSpec): Notification {

        if (SDK_INT > O) {
            val channel = NotificationChannel(specification.channel.id, specification.channel.name, specification.channel.priority)
            channel.description = specification.channel.description
            manager.createNotificationChannel(channel)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val icon = if (SDK_INT >= M) R.drawable.notification_fire else R.drawable.notification_fire_legacy
        val text = context.getString(R.string.clearNotificationDescription)

        return NotificationCompat.Builder(context, specification.channel.id)
            .setPriority(specification.channel.priority)
            .setSmallIcon(icon)
            .setContentTitle(context.getString(R.string.clearNotificationTitle))
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(ContextCompat.getColor(context, R.color.orange))
            .build()
    }

    data class Channel(val id: String, val name: String, val description: String, val priority: Int)

    object Channels {
        val privacyTips = Channel(
            "com.duckduckgo.privacytips",
            "Privacy Tips",
            "Displays helpful privacy tips",
            IMPORTANCE_DEFAULT
        )
    }

    data class NotificationSpec(val id: Int, val channel: Channel, val name: String)

    object NotificationSpecs {
        val autoClear = NotificationSpec(1, Channels.privacyTips, "Update auto clear data")
    }
}