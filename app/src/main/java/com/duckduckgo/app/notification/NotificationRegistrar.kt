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
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import timber.log.Timber
import javax.inject.Inject

class NotificationRegistrar @Inject constructor(
    private val context: Context,
    private val manager: NotificationManager,
    private val compatManager: NotificationManagerCompat,
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel
) {

    data class Channel(
        val id: String,
        @StringRes val name: Int,
        val priority: Int
    )

    object NotificationId {
        const val ClearData = 100
        const val PrivacyProtection = 101
    }

    object ChannelType {
        val FILE_DOWNLOADING = Channel(
            "com.duckduckgo.downloading",
            R.string.notificationChannelFileDownloading,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
        val FILE_DOWNLOADED = Channel(
            "com.duckduckgo.downloaded",
            R.string.notificationChannelFileDownloaded,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
        val TUTORIALS = Channel(
            "com.duckduckgo.tutorials",
            R.string.notificationChannelTutorials,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
    }

    private val channels = (listOf(
        ChannelType.FILE_DOWNLOADING,
        ChannelType.FILE_DOWNLOADED,
        ChannelType.TUTORIALS
    ))

    fun registerApp() {
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
        manager.createNotificationChannels(notificationChannels)
    }

    fun updateStatus() {
        val systemEnabled = compatManager.areNotificationsEnabled()
        val allChannelsEnabled = when {
            SDK_INT >= O -> manager.notificationChannels.all { it.importance != IMPORTANCE_NONE }
            else -> true
        }
        updateStatus(systemEnabled && allChannelsEnabled)
    }

    fun updateStatus(enabled: Boolean) {
        if (settingsDataStore.appNotificationsEnabled != enabled) {
            pixel.fire(if (enabled) Pixel.PixelName.NOTIFICATIONS_ENABLED else Pixel.PixelName.NOTIFICATIONS_DISABLED)
            settingsDataStore.appNotificationsEnabled = enabled
        }
    }
}
