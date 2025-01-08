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

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.TaskStackBuilderFactory
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class PrivacyProtectionNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val privacyProtectionCountDao: PrivacyProtectionCountDao,
) : SchedulableNotification {

    override val id = "com.duckduckgo.privacy.privacyprotection"

    override suspend fun canShow(): Boolean {
        return !notificationDao.exists(id)
    }

    override suspend fun buildSpecification(): NotificationSpec {
        val trackers = privacyProtectionCountDao.getTrackersBlockedCount().toInt()
        val upgrades = privacyProtectionCountDao.getUpgradeCount().toInt()
        return PrivacyProtectionNotificationSpecification(context, trackers, upgrades)
    }
}

class PrivacyProtectionNotificationSpecification(
    context: Context,
    trackers: Int,
    upgrades: Int,
) : NotificationSpec {

    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.PrivacyProtection
    override val name = "Privacy protection"
    override val icon = R.drawable.notification_sheild_lock
    override val launchButton: String = context.getString(R.string.privacyProtectionNotificationLaunchButton)
    override val closeButton: String? = null
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue)

    override val title: String = when {
        trackers < TRACKER_THRESHOLD && upgrades < UPGRADE_THRESHOLD -> context.getString(R.string.privacyProtectionNotificationDefaultTitle)
        else -> context.getString(R.string.privacyProtectionNotificationReportTitle)
    }

    override val description: String = when {
        trackers < TRACKER_THRESHOLD && upgrades < UPGRADE_THRESHOLD -> context.getString(R.string.privacyProtectionNotificationDefaultDescription)
        trackers < TRACKER_THRESHOLD -> context.getString(R.string.privacyProtectionNotificationUpgadeDescription, upgrades)
        upgrades < UPGRADE_THRESHOLD -> context.getString(R.string.privacyProtectionNotificationTrackerDescription, trackers)
        else -> context.getString(R.string.privacyProtectionNotificationBothDescription, trackers, upgrades)
    }

    override val pixelSuffix: String = "pp_${trackers}_$upgrades"

    companion object {
        private const val TRACKER_THRESHOLD = 2
        private const val UPGRADE_THRESHOLD = 2
    }
}

@ContributesMultibinding(AppScope::class)
class PrivacyProtectionNotificationPlugin @Inject constructor(
    private val context: Context,
    private val schedulableNotification: PrivacyProtectionNotification,
    private val taskStackBuilderFactory: TaskStackBuilderFactory,
    private val pixel: Pixel,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
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
        val intent = BrowserActivity.intent(context, newSearch = true, interstitialScreen = true).apply {
            putExtra(BrowserActivity.LAUNCH_FROM_NOTIFICATION_PIXEL_NAME, pixelName(AppPixelName.NOTIFICATION_LAUNCHED.pixelName))
        }
        val pendingIntent: PendingIntent? = taskStackBuilderFactory.createTaskBuilder().run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return pendingIntent
    }

    private fun pixelName(notificationType: String) = "${notificationType}_${getSpecification().pixelSuffix}"
}
