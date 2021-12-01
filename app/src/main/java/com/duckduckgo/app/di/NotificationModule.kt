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
import androidx.work.WorkManager
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.notification.*
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.AppTPWaitlistCodeNotification
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.EmailWaitlistCodeNotification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.waitlist.AppTrackingProtectionWaitlistDataStore
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module(includes = [DaoModule::class])
class NotificationModule {

    @Provides
    @SingleInstanceIn(AppObjectGraph::class)
    fun provideNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Provides
    @SingleInstanceIn(AppObjectGraph::class)
    fun provideNotificationManagerCompat(context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    @Provides
    @SingleInstanceIn(AppObjectGraph::class)
    fun provideLocalBroadcastManager(context: Context): LocalBroadcastManager {
        return LocalBroadcastManager.getInstance(context)
    }

    @Provides
    fun provideClearDataNotification(
        context: Context,
        notificationDao: NotificationDao,
        settingsDataStore: SettingsDataStore
    ): ClearDataNotification {
        return ClearDataNotification(context, notificationDao, settingsDataStore)
    }

    @Provides
    fun providePrivacyProtectionNotification(
        context: Context,
        notificationDao: NotificationDao,
        privacyProtectionCountDao: PrivacyProtectionCountDao
    ): PrivacyProtectionNotification {
        return PrivacyProtectionNotification(context, notificationDao, privacyProtectionCountDao)
    }

    @Provides
    fun provideWaitlistCodeNotification(
        context: Context,
        notificationDao: NotificationDao,
        emailDataStore: EmailDataStore
    ): EmailWaitlistCodeNotification {
        return EmailWaitlistCodeNotification(context, notificationDao, emailDataStore)
    }

    @Provides
    @SingleInstanceIn(AppObjectGraph::class)
    fun providesNotificationScheduler(
        workManager: WorkManager,
        clearDataNotification: ClearDataNotification,
        privacyProtectionNotification: PrivacyProtectionNotification
    ): AndroidNotificationScheduler {
        return NotificationScheduler(
            workManager,
            clearDataNotification,
            privacyProtectionNotification
        )
    }

    @Provides
    @SingleInstanceIn(AppObjectGraph::class)
    fun providesNotificationFactory(context: Context, manager: NotificationManagerCompat): NotificationFactory {
        return NotificationFactory(context, manager)
    }

    @Provides
    @SingleInstanceIn(AppObjectGraph::class)
    fun providesNotificationSender(
        context: Context,
        pixel: Pixel,
        manager: NotificationManagerCompat,
        factory: NotificationFactory,
        notificationDao: NotificationDao
    ): NotificationSender {
        return AppNotificationSender(context, pixel, manager, factory, notificationDao)
    }

    @Provides
    fun provideAppTpWaitlistCodeNotification(
        context: Context,
        notificationDao: NotificationDao,
        dataStore: AppTrackingProtectionWaitlistDataStore
    ): AppTPWaitlistCodeNotification {
        return AppTPWaitlistCodeNotification(context, notificationDao, dataStore)
    }

}
