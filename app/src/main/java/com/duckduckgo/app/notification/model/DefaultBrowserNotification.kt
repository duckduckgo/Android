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

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.TaskStackBuilderFactory
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.isCompetitiveCopyEnabled
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class DefaultBrowserNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val variantManager: VariantManager,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val privacyProtectionNotification: PrivacyProtectionNotification,
) : SchedulableNotification {

    override val id = "com.duckduckgo.privacy.defaultbrowser"

    override suspend fun canShow(): Boolean {
        return !notificationDao.exists(id)
    }

    override suspend fun buildSpecification(): NotificationSpec {
        val title: String
        val description: String
        return if (defaultBrowserDetector.isDefaultBrowser()) {
            privacyProtectionNotification.buildSpecification()
        } else {
            if (variantManager.isCompetitiveCopyEnabled()) {
                title = context.getString(R.string.setAsDefaultCompetitiveCopyNotificationTitle)
                description = context.getString(R.string.setAsDefaultCompetitiveCopyNotificationDescription)
            } else {
                title = context.getString(R.string.setAsDefaultSetupCopyNotificationTitle)
                description = context.getString(R.string.setAsDefaultSetupCopyNotificationDescription)
            }
            DefaultBrowserNotificationSpecification(title, description)
        }
    }
}

class DefaultBrowserNotificationSpecification(
    override val title: String,
    override val description: String,
) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.DefaultBrowser
    override val name = "Default Browser"
    override val icon = com.duckduckgo.mobile.android.R.drawable.notification_logo
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "DefaultBrowser"
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = com.duckduckgo.mobile.android.R.color.ic_launcher_red_background
}

@ContributesMultibinding(AppScope::class)
class DefaultNotificationPlugin @Inject constructor(
    private val context: Context,
    private val schedulableNotification: DefaultBrowserNotification,
    private val taskStackBuilderFactory: TaskStackBuilderFactory,
    private val pixel: Pixel,
    private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SchedulableNotificationPlugin {

    override fun getSchedulableNotification(): SchedulableNotification {
        return schedulableNotification
    }

    override fun onNotificationCancelled() {
        pixel.fire(pixelName(AppPixelName.NOTIFICATION_CANCELLED.pixelName))
    }

    override fun onNotificationShown() {
        pixel.fire(pixelName(AppPixelName.NOTIFICATION_SHOWN.pixelName))
    }

    override fun getSpecification(): NotificationSpec {
        val deferred = coroutineScope.async(dispatcherProvider.io()) {
            schedulableNotification.buildSpecification()
        }
        return runBlocking {
            deferred.await()
        }
    }

    override fun getLaunchIntent(): PendingIntent? {
        val intent = SettingsActivity.intent(context).apply {
            putExtra(SettingsActivity.LAUNCH_FROM_NOTIFICATION_PIXEL_NAME, pixelName(AppPixelName.NOTIFICATION_LAUNCHED.pixelName))
        }
        val pendingIntent: PendingIntent? = taskStackBuilderFactory.createTaskBuilder().run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return pendingIntent
    }

    private fun pixelName(notificationType: String) = "${notificationType}_${getSpecification().pixelSuffix}"
}
