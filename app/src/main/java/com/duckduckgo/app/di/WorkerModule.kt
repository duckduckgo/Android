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

package com.duckduckgo.app.di

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.job.ConfigurationDownloader
import com.duckduckgo.app.notification.NotificationFactory
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.OfflinePixelSender
import com.duckduckgo.app.statistics.pixels.Pixel
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class WorkerModule {

    @Provides
    @Singleton
    fun workManager(context: Context, workerFactory: WorkerFactory): WorkManager {
        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
        WorkManager.initialize(context, config)
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun workerFactory(
        offlinePixelSender: OfflinePixelSender,
        settingsDataStore: SettingsDataStore,
        clearDataAction: ClearDataAction,
        notificationManager: NotificationManagerCompat,
        notificationDao: NotificationDao,
        notificationFactory: NotificationFactory,
        clearDataNotification: ClearDataNotification,
        privacyProtectionNotification: PrivacyProtectionNotification,
        configurationDownloader: ConfigurationDownloader,
        pixel: Pixel
    ): WorkerFactory {
        return DaggerWorkerFactory(
            offlinePixelSender,
            settingsDataStore,
            clearDataAction,
            notificationManager,
            notificationDao,
            notificationFactory,
            clearDataNotification,
            privacyProtectionNotification,
            configurationDownloader,
            pixel
        )
    }
}
