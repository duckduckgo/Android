/*
 * Copyright (c) 2023 DuckDuckGo
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
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.APPTP_LAUNCH
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.isOneEasyStepForPrivacyNotificationEnabled
import timber.log.Timber

class EnableAppTpNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val variantManager: VariantManager,
) : SchedulableNotification {

    override val id = "com.duckduckgo.protection.enableapptp"
    override val launchIntent = APPTP_LAUNCH
    override val cancelIntent = CANCEL

    override suspend fun canShow(): Boolean {
        if (notificationDao.exists(id)) {
            Timber.v("Notification already seen")
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return if (variantManager.isOneEasyStepForPrivacyNotificationEnabled()) {
            OneEasyStepForPrivacySpecification(context)
        } else {
            NextLevelPrivacySpecification(context)
        }
    }
}

class OneEasyStepForPrivacySpecification(context: Context) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.OneEasyStepForPrivacy
    override val name = "One easy step for app privacy"
    override val icon = com.duckduckgo.mobile.android.R.drawable.notification_logo
    override val title: String = context.getString(R.string.enableAppTpNotificationEasyStepTitle)
    override val description: String = context.getString(R.string.enableAppTpNotificationEasyStepDescription)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "OneEasyStepForPrivacy"
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = com.duckduckgo.mobile.android.R.color.ic_launcher_red_background
}

class NextLevelPrivacySpecification(context: Context) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.NextLevelPrivacy
    override val name = "Take your privacy to the next level"
    override val icon = com.duckduckgo.mobile.android.R.drawable.notification_logo
    override val title: String = context.getString(R.string.enableAppTpNotificationNextLevelTitle)
    override val description: String = context.getString(R.string.enableAppTpNotificationNextLevelDescription)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "NextLevelPrivacy"
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = com.duckduckgo.mobile.android.R.color.ic_launcher_red_background
}
