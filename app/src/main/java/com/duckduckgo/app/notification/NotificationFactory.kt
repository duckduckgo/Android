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
import android.app.PendingIntent
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.duckduckgo.app.browser.R
import javax.inject.Inject

class NotificationFactory @Inject constructor(val context: Context, val manager: NotificationManager) {

    data class Channel(
        val id: String,
        val name: String,
        val description: String,
        val priority: Int
    )

    data class NotificationSpec(
        val systemId: Int,
        val id:
        String,
        val channel: Channel,
        val name: String,
        val icon: Int,
        val title: Int,
        val description: Int
    )

    fun createNotification(specification: NotificationSpec, launchIntent: PendingIntent, cancelIntent: PendingIntent): Notification {

        if (SDK_INT > O) {
            createNotificationChannel(specification, manager)
        }

        return NotificationCompat.Builder(context, specification.channel.id)
            .setPriority(specification.channel.priority)
            .setSmallIcon(specification.icon)
            .setContentTitle(context.getString(specification.title))
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(specification.description)))
            .setColor(ContextCompat.getColor(context, R.color.orange))
            .setAutoCancel(true)
            .setContentIntent(launchIntent)
            .setDeleteIntent(cancelIntent)
            .build()
    }

    @RequiresApi(O)
    private fun createNotificationChannel(specification: NotificationSpec, manager: NotificationManager) {
        val channel = NotificationChannel(specification.channel.id, specification.channel.name, specification.channel.priority)
        channel.description = specification.channel.description
        manager.createNotificationChannel(channel)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }
}