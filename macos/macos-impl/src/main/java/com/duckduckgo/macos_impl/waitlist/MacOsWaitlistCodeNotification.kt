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

package com.duckduckgo.macos_impl.waitlist

import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.notification.NotificationRepository
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.NotificationSpec
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.macos_api.MacOsNotificationEvent.MACOS_WAITLIST_CODE
import com.duckduckgo.macos_impl.R
import timber.log.Timber

class MacOsWaitlistCodeNotification(
    private val context: Context,
    private val notificationRepository: NotificationRepository,
    private val manager: MacOsWaitlistManager
) : SchedulableNotification {

    override val id = "com.duckduckgo.vpn.waitlist"
    override val launchIntent = MACOS_WAITLIST_CODE
    override val cancelIntent = "com.duckduckgo.notification.cancel"

    override suspend fun canShow(): Boolean {

        if (notificationRepository.exists(id) || !manager.isNotificationEnabled()) {
            Timber.v("Notification already seen")
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return MacOsWaitlistCodeSpecification(context)
    }
}

class MacOsWaitlistCodeSpecification(context: Context) : NotificationSpec {
    val MACOS_WAITLIST = Channel(
        "com.duckduckgo.email",
        R.string.notification_channel_macos_waitlist,
        NotificationManagerCompat.IMPORTANCE_HIGH
    )

    override val channel = MACOS_WAITLIST
    override val systemId = 200
    override val name = context.getString(R.string.macos_notification_title)
    override val icon = R.drawable.notification_logo
    override val title: String = context.getString(R.string.macos_notification_title)
    override val description: String = context.getString(R.string.macos_notification_text)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "macos"
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = R.color.ic_launcher_red_background
}
