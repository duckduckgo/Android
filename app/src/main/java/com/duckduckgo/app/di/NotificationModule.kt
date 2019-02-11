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

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.duckduckgo.app.notification.NotificationFactory
import com.duckduckgo.app.notification.NotificationScheduler
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(includes = [DaoModule::class])
class NotificationModule {

    @Provides
    @Singleton
    fun provideNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Provides
    @Singleton
    fun provideNotificationManagerCompat(context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    @Provides
    @Singleton
    fun provideLocalBroadcastManager(context: Context): LocalBroadcastManager {
        return LocalBroadcastManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun providesNotificationScheduler(
        notificationDao: NotificationDao,
        notificationManager: NotificationManagerCompat,
        settingsDataStore: SettingsDataStore,
        variantManager: VariantManager
    ): NotificationScheduler {
        return NotificationScheduler(notificationDao, notificationManager, settingsDataStore, variantManager)
    }

    @Provides
    @Singleton
    fun providesNotificationFactory(context: Context, manager: NotificationManagerCompat): NotificationFactory {
        return NotificationFactory(context, manager)
    }
}
