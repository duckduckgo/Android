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
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.WEBSITE
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao

class WebsiteNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val url: String,
    private val pixelSuffix: String
) : SchedulableNotification {

    override val id = "com.duckduckgo.privacy.website"

    override val launchIntent: String = WEBSITE

    override val cancelIntent: String = CANCEL

    override suspend fun canShow(): Boolean {
        return !notificationDao.exists(id)
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return WebsiteNotificationSpecification(context, url, pixelSuffix)
    }

    companion object {
        const val ARTICLE_URL = "https://spreadprivacy.com/privacy-risks-usb-charging"
        const val BLOG_URL = "https://spreadprivacy.com/private-tools-remote-work"
        const val ARTICLE_PIXEL = "an"
        const val BLOG_PIXEL = "bn"
    }
}

open class WebsiteNotificationSpecification(context: Context, url: String, override val pixelSuffix: String) : NotificationSpec {
    override val bundle: Bundle = Bundle().apply { putString(WEBSITE_KEY, url) }

    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.Article
    override val name = "Website"
    override val icon = R.drawable.notification_sheild_lock
    override val launchButton: String = context.getString(R.string.privacyProtectionNotificationLaunchButton)
    override val closeButton: String? = null
    override val autoCancel = true

    override val title: String = "This is the title"

    override val description: String = "This is the description"

    companion object {
        const val WEBSITE_KEY = "websiteKey"
    }
}