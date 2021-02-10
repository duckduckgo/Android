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

package com.duckduckgo.app.global.plugins.worker

import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.fire.DataClearingWorkerInjectorPlugin
import com.duckduckgo.app.global.job.AppConfigurationWorkerInjectorPlugin
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.job.ConfigurationDownloader
import com.duckduckgo.app.notification.ClearDataNotificationWorkerInjectorPlugin
import com.duckduckgo.app.notification.NotificationFactory
import com.duckduckgo.app.notification.PrivacyNotificationWorkerInjectorPlugin
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.OfflinePixelSender
import com.duckduckgo.app.statistics.api.OfflinePixelWorkerInjectorPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet

@Module
class WorkerPluginsModule {

    @Provides
    @IntoSet
    fun dataClearingWorkerInjectorPlugin(
        settingsDataStore: SettingsDataStore,
        clearDataAction: ClearDataAction
    ): WorkerInjectorPlugin = DataClearingWorkerInjectorPlugin(settingsDataStore, clearDataAction)

    @Provides
    @IntoSet
    fun clearDataNotificationWorkerInjectorPlugin(
        notificationManagerCompat: NotificationManagerCompat,
        notificationDao: NotificationDao,
        notificationFactory: NotificationFactory,
        pixel: Pixel,
        clearDataNotification: ClearDataNotification
    ): WorkerInjectorPlugin = ClearDataNotificationWorkerInjectorPlugin(
        notificationManagerCompat,
        notificationDao,
        notificationFactory,
        pixel,
        clearDataNotification
    )

    @Provides
    @IntoSet
    fun privacyNotificationWorkerInjectorPlugin(
        notificationManagerCompat: NotificationManagerCompat,
        notificationDao: NotificationDao,
        notificationFactory: NotificationFactory,
        pixel: Pixel,
        privacyProtectionNotification: PrivacyProtectionNotification
    ): WorkerInjectorPlugin = PrivacyNotificationWorkerInjectorPlugin(
        notificationManagerCompat,
        notificationDao,
        notificationFactory,
        pixel,
        privacyProtectionNotification
    )

    @Provides
    @IntoSet
    fun appConfigurationWorkerInjectorPlugin(
        configurationDownloader: ConfigurationDownloader
    ): WorkerInjectorPlugin = AppConfigurationWorkerInjectorPlugin(configurationDownloader)

    @Provides
    @IntoSet
    fun offlinePixelWorkerInjectorPlugin(
        offlinePixelSender: OfflinePixelSender
    ): WorkerInjectorPlugin = OfflinePixelWorkerInjectorPlugin(offlinePixelSender)
}
