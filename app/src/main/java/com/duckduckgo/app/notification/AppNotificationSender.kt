/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.common.utils.plugins.PluginPoint
import logcat.LogPriority.VERBOSE
import logcat.logcat

class AppNotificationSender(
    private val context: Context,
    private val manager: NotificationManagerCompat,
    private val factory: NotificationFactory,
    private val notificationDao: NotificationDao,
    private val schedulableNotificationPluginPoint: PluginPoint<SchedulableNotificationPlugin>,
) : NotificationSender {

    override suspend fun sendNotification(notification: SchedulableNotification) {
        if (!notification.canShow() || notificationDao.exists(notification.id)) {
            logcat(VERBOSE) { "Notification should not be shown" }
            return
        }

        val specification = notification.buildSpecification()

        val notificationPlugin = schedulableNotificationPluginPoint.getPlugins().firstOrNull {
            notification.javaClass == it.getSchedulableNotification().javaClass
        }

        if (notificationPlugin == null) {
            logcat(VERBOSE) { "No plugin found for notification class ${notification.javaClass}" }
            return
        }

        val launchIntent = notificationPlugin.getLaunchIntent()
        val cancelIntent = NotificationHandlerService.pendingCancelNotificationHandlerIntent(context, notification.javaClass)
        val systemNotification = factory.createNotification(specification, launchIntent, cancelIntent)
        notificationDao.insert(Notification(notification.id))
        manager.checkPermissionAndNotify(context, specification.systemId, systemNotification)

        notificationPlugin.onNotificationShown()
    }
}
