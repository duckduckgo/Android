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

package com.duckduckgo.app.di

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkManager
import com.duckduckgo.app.notification.*
import com.duckduckgo.app.notification.AndroidNotificationScheduler
import com.duckduckgo.app.notification.NotificationScheduler
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module(includes = [DaoModule::class])
object NotificationModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideNotificationManagerCompat(context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideLocalBroadcastManager(context: Context): LocalBroadcastManager {
        return LocalBroadcastManager.getInstance(context)
    }

    @Provides
    fun provideClearDataNotification(
        context: Context,
        notificationDao: NotificationDao,
        settingsDataStore: SettingsDataStore,
    ): ClearDataNotification {
        return ClearDataNotification(context, notificationDao, settingsDataStore)
    }

    @Provides
    fun providePrivacyProtectionNotification(
        context: Context,
        notificationDao: NotificationDao,
        privacyProtectionCountDao: PrivacyProtectionCountDao,
    ): PrivacyProtectionNotification {
        return PrivacyProtectionNotification(context, notificationDao, privacyProtectionCountDao)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesNotificationScheduler(
        workManager: WorkManager,
        clearDataNotification: ClearDataNotification,
        privacyProtectionNotification: PrivacyProtectionNotification,
    ): AndroidNotificationScheduler {
        return NotificationScheduler(
            workManager,
            clearDataNotification,
            privacyProtectionNotification,
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesNotificationFactory(
        context: Context,
        manager: NotificationManagerCompat,
    ): NotificationFactory {
        return NotificationFactory(context, manager)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesNotificationSender(
        context: Context,
        manager: NotificationManagerCompat,
        factory: NotificationFactory,
        notificationDao: NotificationDao,
        pluginPoint: PluginPoint<SchedulableNotificationPlugin>,
    ): NotificationSender {
        return AppNotificationSender(context, manager, factory, notificationDao, pluginPoint)
    }
}
