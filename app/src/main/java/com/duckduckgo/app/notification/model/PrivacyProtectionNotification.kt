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

package com.duckduckgo.app.notification.model

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.APP_LAUNCH
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao


class PrivacyProtectionNotification(private val notificationDao: NotificationDao) : SchedulableNotification {

    override val specification = PrivacyProtectionNotificationSpecification()

    override val launchIntent: String
        get() = APP_LAUNCH

    override val cancelIntent: String
        get() = CANCEL

    override suspend fun canShow(): Boolean {
        return !notificationDao.exists(specification.id)
    }
}

class PrivacyProtectionNotificationSpecification : NotificationSpec {
    override val systemId = 101
    override val id = "com.duckduckgo.privacy.privacyprotection"
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val name = "Privacy protection"
    override val icon = R.drawable.notification_sheild_lock
    override val title = R.string.privacyProtectionNotificationTitle
    override val description = R.string.privacyProtectionNotificationDescription
}