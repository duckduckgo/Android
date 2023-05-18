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

package com.duckduckgo.windows.impl.waitlist

import android.app.PendingIntent
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
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.windows.api.WindowsDownloadLinkFeature
import com.duckduckgo.windows.api.ui.WindowsScreenWithEmptyParams
import com.duckduckgo.windows.impl.R
import com.duckduckgo.windows.impl.WindowsPixelNames.WINDOWS_WAITLIST_NOTIFICATION_CANCELLED
import com.duckduckgo.windows.impl.WindowsPixelNames.WINDOWS_WAITLIST_NOTIFICATION_LAUNCHED
import com.duckduckgo.windows.impl.WindowsPixelNames.WINDOWS_WAITLIST_NOTIFICATION_SHOWN
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistActivity
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import timber.log.Timber

@ContributesBinding(AppScope::class)
class WindowsWaitlistCodeNotification @Inject constructor(
    private val context: Context,
    private val notificationRepository: NotificationRepository,
) : SchedulableNotification {

    override val id = "com.duckduckgo.windows.waitlist"

    override suspend fun canShow(): Boolean {
        if (notificationRepository.exists(id)) {
            Timber.v("Notification already seen")
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return WindowsWaitlistCodeSpecification(context)
    }
}

class WindowsWaitlistCodeSpecification(context: Context) : NotificationSpec {
    override val channel = Channel(
        "com.duckduckgo.windows",
        R.string.notification_channel_windows_waitlist,
        NotificationManagerCompat.IMPORTANCE_HIGH,
    )

    override val systemId = 200
    override val name = context.getString(R.string.windows_notification_title)
    override val icon = com.duckduckgo.mobile.android.R.drawable.notification_logo
    override val title: String = context.getString(R.string.windows_notification_title)
    override val description: String = context.getString(R.string.windows_notification_text)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "windows"
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = com.duckduckgo.mobile.android.R.color.ic_launcher_red_background
}

@ContributesMultibinding(AppScope::class)
class WindowsWaitlistNotificationPlugin @Inject constructor(
    private val context: Context,
    private val schedulableNotification: SchedulableNotification,
    private val taskStackBuilderFactory: TaskStackBuilderFactory,
    private val pixel: Pixel,
    private val windowsDownloadLinkFeature: WindowsDownloadLinkFeature,
    private val globalActivityStarter: GlobalActivityStarter,
) : SchedulableNotificationPlugin {

    override fun getSchedulableNotification(): SchedulableNotification {
        return schedulableNotification
    }

    override fun getSpecification(): NotificationSpec {
        return WindowsWaitlistCodeSpecification(context)
    }

    override fun onNotificationCancelled() {
        pixel.fire(WINDOWS_WAITLIST_NOTIFICATION_CANCELLED)
    }

    override fun onNotificationShown() {
        pixel.fire(WINDOWS_WAITLIST_NOTIFICATION_SHOWN)
    }

    override fun getLaunchIntent(): PendingIntent? {
        val intent = if (windowsDownloadLinkFeature.self().isEnabled()) {
            globalActivityStarter.startIntent(context, WindowsScreenWithEmptyParams)
        } else {
            WindowsWaitlistActivity.intent(context).apply {
                putExtra(WindowsWaitlistActivity.LAUNCH_FROM_NOTIFICATION_PIXEL_NAME, WINDOWS_WAITLIST_NOTIFICATION_LAUNCHED.pixelName)
            }
        }
        val pendingIntent: PendingIntent? = taskStackBuilderFactory.createTaskBuilder().run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return pendingIntent
    }
}
