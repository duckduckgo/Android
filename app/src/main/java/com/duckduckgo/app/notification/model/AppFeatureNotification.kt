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
import android.os.Bundle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.APP_FEATURE
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao

class AppFeatureNotification(
    private val context: Context,
    private val notificationDao: NotificationDao
) : SchedulableNotification {

    override val id = "com.duckduckgo.privacy.app.feature"

    override val launchIntent: String = APP_FEATURE

    override val cancelIntent: String = CANCEL

    override suspend fun canShow(): Boolean {
        return !notificationDao.exists(id)
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return AppFeatureNotificationSpecification(context)
    }
}

class AppFeatureNotificationSpecification(context: Context) : NotificationSpec {
    override val bundle: Bundle = Bundle()

    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.AppFeature
    override val name = "AppFeature"
    override val icon = R.drawable.notification_sheild_lock
    override val launchButton: String = context.getString(R.string.privacyProtectionNotificationLaunchButton)
    override val closeButton: String? = null
    override val autoCancel = true

    override val title: String = "This is the title"

    override val description: String = "This is the description"

    override val pixelSuffix: String = "fn"
}