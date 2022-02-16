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

package com.duckduckgo.macos_impl.waitlist

import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.notification.NotificationRepository
import com.duckduckgo.app.notification.TaskStackBuilderFactory
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.NotificationSpec
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.macos_impl.MacOsPixelNames.MACOS_WAITLIST_NOTIFICATION_CANCELLED
import com.duckduckgo.macos_impl.MacOsPixelNames.MACOS_WAITLIST_NOTIFICATION_LAUNCHED
import com.duckduckgo.macos_impl.MacOsPixelNames.MACOS_WAITLIST_NOTIFICATION_SHOWN
import com.duckduckgo.macos_impl.R
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistActivity
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class MacOsWaitlistCodeNotification @Inject constructor(
    private val context: Context,
    private val notificationRepository: NotificationRepository,
    private val manager: MacOsWaitlistManager
) : SchedulableNotification {

    override val id = "com.duckduckgo.macos.waitlist"
    override val launchIntent = "com.duckduckgo.notification.macos.waitlist.code"
    override val cancelIntent: String = "com.duckduckgo.notification.macos.waitlist.code.cancel"

    override suspend fun canShow(): Boolean {

        if (notificationRepository.exists(id) || !manager.isNotificationEnabled()) {
            Timber.v("Notification already seen")
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return MacOsWaitlistCodeSpecification(context)
    }
}

class MacOsWaitlistCodeSpecification(context: Context) : NotificationSpec {
    override val channel = Channel(
        "com.duckduckgo.macos",
        R.string.notification_channel_macos_waitlist,
        NotificationManagerCompat.IMPORTANCE_HIGH
    )

    override val systemId = 200
    override val name = context.getString(R.string.macos_notification_title)
    override val icon = com.duckduckgo.mobile.android.R.drawable.notification_logo
    override val title: String = context.getString(R.string.macos_notification_title)
    override val description: String = context.getString(R.string.macos_notification_text)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "macos"
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = com.duckduckgo.mobile.android.R.color.ic_launcher_red_background
}

@ContributesMultibinding(AppScope::class)
class MacOsWaitlistNotificationPlugin @Inject constructor(
    private val context: Context,
    private val schedulableNotification: SchedulableNotification,
    private val taskStackBuilderFactory: TaskStackBuilderFactory,
    private val pixel: Pixel
) : SchedulableNotificationPlugin {

    override fun getSchedulableNotification(): SchedulableNotification {
        return schedulableNotification
    }

    override fun getSpecification(): NotificationSpec {
        return MacOsWaitlistCodeSpecification(context)
    }

    override fun onNotificationCancelled() {
        pixel.fire(MACOS_WAITLIST_NOTIFICATION_CANCELLED)
    }

    override fun onNotificationShown() {
        pixel.fire(MACOS_WAITLIST_NOTIFICATION_SHOWN)
    }

    override fun onNotificationLaunched() {
        pixel.fire(MACOS_WAITLIST_NOTIFICATION_LAUNCHED)
        val intent = MacOsWaitlistActivity.intent(context)
        taskStackBuilderFactory.createTaskBuilder()
            .addNextIntentWithParentStack(intent)
            .startActivities()
    }
}
