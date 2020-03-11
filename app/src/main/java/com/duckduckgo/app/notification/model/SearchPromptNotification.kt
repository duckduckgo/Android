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
import com.duckduckgo.app.notification.NotificationHandlerService
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import timber.log.Timber

class SearchPromptNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val settingsDataStore: SettingsDataStore
) : SearchNotification {

    override val id = "com.duckduckgo.privacy.search.stickyPrompt"

    override val pressIntent = NotificationHandlerService.NotificationEvent.QUICK_SEARCH_PROMPT_LAUNCH

    override val launchIntent = NotificationHandlerService.NotificationEvent.QUICK_SEARCH_KEEP

    override val cancelIntent = NotificationHandlerService.NotificationEvent.QUICK_SEARCH_REMOVE

    override val layoutId = R.layout.search_notification_prompt

    override val priority = NotificationManagerCompat.IMPORTANCE_DEFAULT

    override suspend fun canShow(): Boolean {
        if (notificationDao.exists(id)) {
            Timber.v("Notification already seen")
            return false
        }

        if (settingsDataStore.searchNotificationEnabled) {
            Timber.v("Notification is already enabled")
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return StickySearchPromptSpecification(context)
    }
}

class StickySearchPromptSpecification(context: Context) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.StickySearch
    override val name = "Add sticky search notification"
    override val icon = R.drawable.notification_logo
    override val title: String = context.getString(R.string.stickySearchPromptNotificationTitle)
    override val description: String = context.getString(R.string.stickySearchPromptNotificationTitle)
    override val launchButton: String = context.getString(R.string.stickySearchPromptKeep)
    override val closeButton: String = context.getString(R.string.stickySearchPromptRemove)
    override val pixelSuffix = ""
    override val autoCancel = false
}

