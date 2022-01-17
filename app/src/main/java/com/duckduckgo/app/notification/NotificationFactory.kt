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
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duckduckgo.app.notification.model.NotificationSpec
import javax.inject.Inject

class NotificationFactory @Inject constructor(
    val context: Context,
    val manager: NotificationManagerCompat
) {

    fun createNotification(
        specification: NotificationSpec,
        launchIntent: PendingIntent,
        cancelIntent: PendingIntent
    ): Notification {

        val builder = NotificationCompat.Builder(context, specification.channel.id)
            .setPriority(specification.channel.priority)
            .setSmallIcon(specification.icon)
            .setContentTitle(specification.title)
            .setContentText(specification.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(specification.description))
            .setColor(ContextCompat.getColor(context, specification.color))
            .setContentIntent(launchIntent)
            .setDeleteIntent(cancelIntent)

        specification.launchButton?.let {
            builder.addAction(specification.icon, it, launchIntent)
        }

        specification.closeButton?.let {
            builder.addAction(specification.icon, it, cancelIntent)
        }

        return builder.build()
    }
}
