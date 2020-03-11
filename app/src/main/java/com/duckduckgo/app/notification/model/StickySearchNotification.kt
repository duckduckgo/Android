/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.notification.model

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.STICKY_SEARCH_QUERY
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao
import timber.log.Timber

class StickySearchNotification(
    private val context: Context,
    private val notificationDao: NotificationDao
) : SearchNotification {

    override val id = "com.duckduckgo.privacy.search.sticky"

    override val launchIntent = STICKY_SEARCH_QUERY

    override val cancelIntent = STICKY_SEARCH_QUERY

    override val layoutId = R.layout.search_notification

    override val priority = NotificationManagerCompat.IMPORTANCE_DEFAULT

    override suspend fun canShow(): Boolean {

        if (notificationDao.exists(id)) {
            Timber.v("Notification already seen")
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return StickySearchNotificationSpecification(context)
    }
}

class StickySearchNotificationSpecification(context: Context) : NotificationSpec {

    override val channel = NotificationRegistrar.ChannelType.SEARCH
    override val systemId = NotificationRegistrar.NotificationId.StickySearch
    override val name = context.getString(R.string.stickySearchNotification)
    override val icon = R.drawable.notification_logo
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val title: String = "Start a Search"
    override val description: String = "You can now search from anywhere!"
    override val pixelSuffix: String = ""
    override val autoCancel = false
}