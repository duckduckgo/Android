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
import com.duckduckgo.app.browser.R
import javax.inject.Inject

class NotificationFactory @Inject constructor(val context: Context, val manager: NotificationManagerCompat) {

    fun createNotification(
        specification: NotificationScheduler.NotificationSpec,
        launchIntent: PendingIntent,
        cancelIntent: PendingIntent
    ): Notification {
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
}