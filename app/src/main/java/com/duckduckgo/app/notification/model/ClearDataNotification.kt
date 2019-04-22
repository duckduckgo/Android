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

import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_LAUNCH
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import timber.log.Timber

class ClearDataNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val settingsDataStore: SettingsDataStore
) : SchedulableNotification {

    override val id = "com.duckduckgo.privacytips.autoclear"
    override val launchIntent = CLEAR_DATA_LAUNCH
    override val cancelIntent = CANCEL

    override suspend fun canShow(): Boolean {

        if (notificationDao.exists(id)) {
            Timber.v("Notification already seen")
            return false
        }

        if (settingsDataStore.automaticallyClearWhatOption != ClearWhatOption.CLEAR_NONE) {
            Timber.v("No need for notification, user already has clear option set")
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return ClearDataSpecification(context)
    }
}

class ClearDataSpecification(context: Context) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.ClearData
    override val name = "Update auto clear data"
    override val icon = R.drawable.notification_fire
    override val title: String = context.getString(R.string.clearNotificationTitle)
    override val description: String = context.getString(R.string.clearNotificationDescription)
    override val launchButton: String? = null
    override val pixelSuffix = "cd"
}