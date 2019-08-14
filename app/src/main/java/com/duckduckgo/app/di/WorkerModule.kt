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

import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkerFactory
import com.duckduckgo.app.global.view.ClearDataAction
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
    fun workerFactory(
        offlinePixelSender: OfflinePixelSender,
        settingsDataStore: SettingsDataStore,
        clearDataAction: ClearDataAction,
        notficationManager: NotificationManagerCompat,
        notificationDao: NotificationDao,
        notificationFactory: NotificationFactory,
        clearDataNotification: ClearDataNotification,
        privacyProtectionNotification: PrivacyProtectionNotification,
        pixel: Pixel
    ): WorkerFactory {
        return DaggerWorkerFactory(
            offlinePixelSender,
            settingsDataStore,
            clearDataAction,
            notficationManager,
            notificationDao,
            notificationFactory,
            clearDataNotification,
            privacyProtectionNotification,
            pixel
        )
    }
}