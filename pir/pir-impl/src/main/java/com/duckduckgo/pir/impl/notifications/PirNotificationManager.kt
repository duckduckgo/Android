/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.pir.impl.notifications.PirNotificationManager.Companion.PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface PirNotificationManager {
    fun createNotificationChannel()

    companion object {
        const val PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID = "com.duckduckgo.pir.PirNotificationChannel"
        const val PIR_FOREGROUND_SERVICE_NOTIFICATION_ID_STATUS_COMPLETE = 987
    }
}

@ContributesBinding(ActivityScope::class)
class RealPirNotificationManager @Inject constructor(
    private val context: Context,
) : PirNotificationManager {

    override fun createNotificationChannel() {
        // Define the importance level of the notification channel
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        // Create the NotificationChannel with a unique ID, name, and importance level
        val channel =
            NotificationChannel(PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID, PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_NAME, importance)
        channel.description = PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_DESCRIPTION

        // Register the channel with the system
        val notificationManager = context.getSystemService(
            NotificationManager::class.java,
        )
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        private const val PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_NAME = "Personal Information Removal Status"
        private const val PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_DESCRIPTION = "Status updates for Personal Information Removal"
    }
}
