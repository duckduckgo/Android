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
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.notification.AndroidNotificationScheduler
import com.duckduckgo.app.notification.NotificationFactory
import com.duckduckgo.app.notification.NotificationHandlerService
import com.duckduckgo.app.notification.NotificationScheduler
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.AppFeatureNotification
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.UseOurAppNotification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.notification.model.WebsiteNotification
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import dagger.Module
import dagger.Provides
import javax.inject.Named
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
    fun provideUseOurAppNotification(
        context: Context,
        notificationDao: NotificationDao,
        settingsDataStore: SettingsDataStore,
        addToHomeCapabilityDetector: AddToHomeCapabilityDetector
    ): UseOurAppNotification {
        return UseOurAppNotification(context, notificationDao, settingsDataStore, addToHomeCapabilityDetector)
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
    @Named("dripA1Notification")
    fun provideDripA1Notification(
        context: Context,
        notificationDao: NotificationDao
    ): WebsiteNotification {
        return WebsiteNotification(
            context,
            notificationDao,
            WebsiteNotification.DRIP_A_1_URL,
            WebsiteNotification.DRIP_A_1_TITLE,
            WebsiteNotification.DRIP_A_1_DESCRIPTION,
            WebsiteNotification.DRIP_A_1_PIXEL
        )
    }

    @Provides
    @Named("dripA2Notification")
    fun provideDripA2Notification(
        context: Context,
        notificationDao: NotificationDao
    ): WebsiteNotification {
        return WebsiteNotification(
            context,
            notificationDao,
            WebsiteNotification.DRIP_A_2_URL,
            WebsiteNotification.DRIP_A_2_TITLE,
            WebsiteNotification.DRIP_A_2_DESCRIPTION,
            WebsiteNotification.DRIP_A_2_PIXEL
        )
    }

    @Provides
    @Named("dripB1Notification")
    fun provideADripB1Notification(
        context: Context,
        notificationDao: NotificationDao
    ): AppFeatureNotification {
        return AppFeatureNotification(
            context,
            notificationDao,
            AppFeatureNotification.DRIP_B_1_TITLE,
            AppFeatureNotification.DRIP_B_1_DESCRIPTION,
            AppFeatureNotification.DRIP_B_1_PIXEL,
            NotificationHandlerService.NotificationEvent.CHANGE_ICON_FEATURE
        )
    }

    @Provides
    @Named("dripB2Notification")
    fun provideADripB2Notification(
        context: Context,
        notificationDao: NotificationDao
    ): AppFeatureNotification {
        return AppFeatureNotification(
            context,
            notificationDao,
            AppFeatureNotification.DRIP_B_2_TITLE,
            AppFeatureNotification.DRIP_B_2_DESCRIPTION,
            AppFeatureNotification.DRIP_B_2_PIXEL
        )
    }

    @Provides
    @Singleton
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
    @Singleton
    fun providesNotificationFactory(context: Context, manager: NotificationManagerCompat): NotificationFactory {
        return NotificationFactory(context, manager)
    }
}
