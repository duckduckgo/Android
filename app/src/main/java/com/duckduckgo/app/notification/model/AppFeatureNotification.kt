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
import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.APP_LAUNCH
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao

class AppFeatureNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    @StringRes private val title: Int,
    @StringRes private val description: Int,
    private val pixelSuffix: String,
    override val launchIntent: String = APP_LAUNCH
) : SchedulableNotification {

    override val id = "com.duckduckgo.privacy.app.feature.$pixelSuffix"

    override val cancelIntent: String = CANCEL

    override suspend fun canShow(): Boolean {
        return !notificationDao.exists(id)
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return AppFeatureNotificationSpecification(context, title, description, pixelSuffix)
    }
}

class AppFeatureNotificationSpecification(
    context: Context,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    override val pixelSuffix: String
) : NotificationSpec {
    override val bundle: Bundle = Bundle()
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.AppFeature
    override val name = "AppFeature"
    override val icon = R.drawable.notification_logo
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val autoCancel = true
    override val title: String = context.getString(titleRes)
    override val description: String = context.getString(descriptionRes)
    override val color: Int = R.color.ic_launcher_red_background
}
