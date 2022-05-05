/*
 * Copyright (c) 2018 DuckDuckGo
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

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_NONE
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.browser.R
import com.duckduckgo.mobile.android.vpn.R as VpnR
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.impl.FileDownloadNotificationChannelType
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class NotificationRegistrar @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val context: Context,
    private val manager: NotificationManager,
    private val compatManager: NotificationManagerCompat,
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val schedulableNotificationPluginPoint: PluginPoint<SchedulableNotificationPlugin>
) : LifecycleObserver {

    object NotificationId {
        const val ClearData = 100
        const val PrivacyProtection = 101
        const val Article = 103 // 102 was used for the search notification hence using 103 moving forward
        const val AppFeature = 104
        const val EmailWaitlist = 106 // 105 was used for the UOA notification
    }

    object ChannelType {
        val FILE_DOWNLOADING = FileDownloadNotificationChannelType.FILE_DOWNLOADING
        val FILE_DOWNLOADED = FileDownloadNotificationChannelType.FILE_DOWNLOADED
        val TUTORIALS = Channel(
            "com.duckduckgo.tutorials",
            R.string.notificationChannelTutorials,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
        val EMAIL_WAITLIST = Channel(
            "com.duckduckgo.email",
            R.string.notificationChannelEmailWaitlist,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
        val APP_TP_WAITLIST = Channel(
            "com.duckduckgo.apptp",
            VpnR.string.atp_WaitlistActivityWaitlistTitle,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
        // Do not add new channels here, instead follow https://app.asana.com/0/1125189844152671/1201842645469204
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onApplicationCreated() {
        appCoroutineScope.launch { registerApp() }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun updateNotificationStatus() {
        val systemEnabled = compatManager.areNotificationsEnabled()
        val allChannelsEnabled = when {
            SDK_INT >= O -> manager.notificationChannels.all { it.importance != IMPORTANCE_NONE }
            else -> true
        }

        updateStatus(systemEnabled && allChannelsEnabled)
    }

    private val channels = listOf(
        ChannelType.FILE_DOWNLOADING,
        ChannelType.FILE_DOWNLOADED,
        ChannelType.TUTORIALS,
        ChannelType.EMAIL_WAITLIST,
        ChannelType.APP_TP_WAITLIST
    )

    private fun registerApp() {
        if (SDK_INT < O) {
            Timber.d("No need to register for notification channels on this SDK version")
            return
        }
        configureNotificationChannels()
    }

    @TargetApi(O)
    private fun configureNotificationChannels() {
        val notificationChannels = channels.map {
            NotificationChannel(it.id, context.getString(it.name), it.priority)
        }
        val pluginChannels = schedulableNotificationPluginPoint.getPlugins().map {
            val channel = it.getSpecification().channel
            NotificationChannel(channel.id, context.getString(channel.name), channel.priority)
        }
        manager.createNotificationChannels(notificationChannels + pluginChannels)
    }

    @VisibleForTesting
    fun updateStatus(enabled: Boolean) {
        if (settingsDataStore.appNotificationsEnabled != enabled) {
            pixel.fire(if (enabled) AppPixelName.NOTIFICATIONS_ENABLED else AppPixelName.NOTIFICATIONS_DISABLED)
            settingsDataStore.appNotificationsEnabled = enabled
        }
    }
}
