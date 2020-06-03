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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.NotificationHandlerService
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.APP_LAUNCH
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_LAUNCH
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import timber.log.Timber

class FacebookNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val userStageStore: UserStageStore
) : SchedulableNotification {

    override val id = "com.duckduckgo.privacytips.fb"
    override val launchIntent = NotificationHandlerService.NotificationEvent.START_FB_FLOW
    override val cancelIntent = CANCEL

    override suspend fun canShow(): Boolean {
        if (notificationDao.exists(id)) {
            Timber.v("MARCOS Notification already seen")
            return false
        }

        if (userStageStore.getUserAppStage() != AppStage.ESTABLISHED) {
            Timber.v("MARCOS User not in established state")
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return FacebookSpecification(context)
    }
}

class FacebookSpecification(context: Context) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.Facebook
    override val name = "Update auto clear data"
    override val icon = R.drawable.notification_logo
    override val title: String = "Worried about Facebook tracking you?"
    override val description: String = "Here's a simply way to reduce it's reach."
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "fb"
    override val autoCancel = true
}
