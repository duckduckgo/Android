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
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.notification.NotificationHandlerService
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import timber.log.Timber

class UseOurAppNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val settingsDataStore: SettingsDataStore,
    private val addToHomeCapabilityDetector: AddToHomeCapabilityDetector
) : SchedulableNotification {

    override val id = ID
    override val launchIntent = NotificationHandlerService.NotificationEvent.USE_OUR_APP
    override val cancelIntent = CANCEL

    override suspend fun canShow(): Boolean {
        if (notificationDao.exists(id) || settingsDataStore.hideTips || !addToHomeCapabilityDetector.isAddToHomeSupported()) {
            Timber.v("Notification already seen")
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return UseOurAppSpecification(context)
    }

    companion object {
        const val ID = "com.duckduckgo.privacytips.useOurApp"
    }
}

class UseOurAppSpecification(context: Context) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.UseOurApp
    override val name = "Use our app"
    override val icon = R.drawable.notification_logo
    override val title: String = context.getString(R.string.useOurAppNotificationTitle)
    override val description: String = context.getString(R.string.useOurAppNotificationDescription)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = Pixel.PixelName.USE_OUR_APP_NOTIFICATION_SUFFIX.pixelName
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = R.color.ic_launcher_red_background
}
