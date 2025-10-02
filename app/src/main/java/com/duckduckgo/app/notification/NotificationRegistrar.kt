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

import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_NONE
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.NotificationPlugin
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class NotificationRegistrar @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val context: Context,
    private val compatManager: NotificationManagerCompat,
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val schedulableNotificationPluginPoint: PluginPoint<SchedulableNotificationPlugin>,
    private val notificationPluginPoint: PluginPoint<NotificationPlugin>,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    object NotificationId {
        const val ClearData = 100
        const val PrivacyProtection = 101
        const val Article = 103 // 102 was used for the search notification hence using 103 moving forward
        const val AppFeature = 104
        const val SurveyAvailable = 109 // 105 to 108 were already used previously
        // 110 was already used previously
    }

    object ChannelType {
        val TUTORIALS = Channel(
            "com.duckduckgo.tutorials",
            R.string.notificationChannelTutorials,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
        // Do not add new channels here, instead follow https://app.asana.com/0/1125189844152671/1201842645469204
    }

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) { registerApp() }
    }

    @Suppress("NewApi") // we use NotificationCompatManager to retrieve channels
    override fun onResume(owner: LifecycleOwner) {
        val systemEnabled = compatManager.areNotificationsEnabled()
        val allChannelsEnabled = compatManager.notificationChannels.all { it.importance != IMPORTANCE_NONE }
        updateStatus(systemEnabled && allChannelsEnabled)
    }

    private val channels = listOf(
        ChannelType.TUTORIALS,
    )

    private fun registerApp() {
        configureNotificationChannels()
    }

    private fun configureNotificationChannels() {
        val notificationChannels = channels.map {
            NotificationChannel(it.id, context.getString(it.name), it.priority)
        }
        val pluginChannels = schedulableNotificationPluginPoint.getPlugins().map {
            val channel = it.getSpecification().channel
            NotificationChannel(channel.id, context.getString(channel.name), channel.priority)
        } + notificationPluginPoint.getPlugins().map { it.getChannels() }.flatMap {
            val list = mutableListOf<NotificationChannel>().apply {
                for (channel in it) {
                    add(NotificationChannel(channel.id, context.getString(channel.name), channel.priority))
                }
            }
            list.toList()
        }
        compatManager.createNotificationChannels(notificationChannels + pluginChannels)

        // TODO this is hack because we don't have a good way to remove channels when we no longer use them
        // if we don't call deleteNotificationChannel() method, the channel only disappears on fresh installs, not app updates
        // See https://app.asana.com/0/1125189844152671/1201842645469204 for more info
        // This was the AppTP waitlist notification channel
        compatManager.deleteNotificationChannel("com.duckduckgo.apptp")
    }

    @VisibleForTesting
    fun updateStatus(enabled: Boolean) {
        if (settingsDataStore.appNotificationsEnabled != enabled) {
            pixel.fire(if (enabled) AppPixelName.NOTIFICATIONS_ENABLED else AppPixelName.NOTIFICATIONS_DISABLED)
            settingsDataStore.appNotificationsEnabled = enabled
        }
    }
}
