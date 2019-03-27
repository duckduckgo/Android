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
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.APP_LAUNCH
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import kotlinx.coroutines.runBlocking


class PrivacyProtectionNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val privacyProtectionCountDao: PrivacyProtectionCountDao
) : SchedulableNotification {

    override val specification
        get() = PrivacyProtectionNotificationSpecification(context, privacyProtectionCountDao)

    override val launchIntent: String
        get() = APP_LAUNCH

    override val cancelIntent: String
        get() = CANCEL

    override suspend fun canShow(): Boolean {
        return !notificationDao.exists(specification.id)
    }
}

class PrivacyProtectionNotificationSpecification(
    private val context: Context,
    private val privacyProtectionCountDao: PrivacyProtectionCountDao
) : NotificationSpec {

    override val systemId = 101
    override val id = "com.duckduckgo.privacy.privacyprotection"
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val name = "Privacy protection"
    override val icon = R.drawable.notification_sheild_lock

    override val title: String
        get() {
            val trackers = runBlocking { privacyProtectionCountDao.getTrackersBlockedCount().toInt() }
            val upgrades = runBlocking { privacyProtectionCountDao.getUpgradeCount().toInt() }
            return when {
                trackers < TRACKER_THRESHOLD && upgrades < UPGADE_THRESHOLD -> context.getString(R.string.privacyProtectionNotificationDefaultTitle)
                else -> context.getString(R.string.privacyProtectionNotificationReportTitle)
            }
        }

    override val description: String
        get() {
            val trackers = runBlocking { privacyProtectionCountDao.getTrackersBlockedCount().toInt() }
            val upgrades = runBlocking { privacyProtectionCountDao.getUpgradeCount().toInt() }
            return when {
                trackers < TRACKER_THRESHOLD && upgrades < UPGADE_THRESHOLD -> context.getString(R.string.privacyProtectionNotificationDefaultDescription)
                trackers < TRACKER_THRESHOLD -> context.getString(R.string.privacyProtectionNotificationUpgadeDescription, upgrades)
                upgrades < UPGADE_THRESHOLD -> context.getString(R.string.privacyProtectionNotificationTrackerDescription, trackers)
                else -> context.getString(R.string.privacyProtectionNotificationBothDescription, trackers, upgrades)
            }
        }

    companion object {
        private val TRACKER_THRESHOLD = 2
        private val UPGADE_THRESHOLD = 2
    }
}