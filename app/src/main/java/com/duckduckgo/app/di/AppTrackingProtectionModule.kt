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

package com.duckduckgo.app.di

import android.content.Context
import androidx.lifecycle.LifecycleObserver
import androidx.work.WorkManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.AppTPWaitlistCodeNotification
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.waitlist.trackerprotection.AppTrackingProtectionWaitlistCodeFetcher
import com.duckduckgo.app.waitlist.trackerprotection.TrackingProtectionWaitlistCodeFetcher
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.waitlist.AppTrackingProtectionWaitlistDataStore
import com.duckduckgo.mobile.android.vpn.waitlist.AppTrackingProtectionWaitlistManager
import com.duckduckgo.mobile.android.vpn.waitlist.TrackingProtectionWaitlistManager
import com.duckduckgo.mobile.android.vpn.waitlist.api.AppTrackingProtectionWaitlistService
import com.duckduckgo.mobile.android.vpn.waitlist.store.AppTrackingProtectionEncryptedSharedPreferences
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
class AppTrackingProtectionModule {

    @Singleton
    @Provides
    fun providesAppTrackingProtectionWaitlistManager(
        service: AppTrackingProtectionWaitlistService,
        dataStore: AppTrackingProtectionWaitlistDataStore,
        dispatcherProvider: DispatcherProvider
    ): TrackingProtectionWaitlistManager {
        return AppTrackingProtectionWaitlistManager(service, dataStore, dispatcherProvider)
    }

    @Provides
    fun providesAppTrackingProtectionWaitlistDataStore(
        context: Context,
        pixel: Pixel
    ): AppTrackingProtectionWaitlistDataStore {
        return AppTrackingProtectionEncryptedSharedPreferences(context, pixel)
    }

    @Singleton
    @Provides
    fun providesAppTrackingProtectionWaitlistCodeFetcher(
        workManager: WorkManager,
        manager: TrackingProtectionWaitlistManager,
        notification: AppTPWaitlistCodeNotification,
        notificationSender: NotificationSender,
        dispatcherProvider: DispatcherProvider,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): TrackingProtectionWaitlistCodeFetcher {
        return AppTrackingProtectionWaitlistCodeFetcher(workManager, manager, notification, notificationSender, dispatcherProvider, appCoroutineScope)
    }

    @Provides
    @Singleton
    @IntoSet
    fun providesAppTrackingProtectionCodeFetcherObserver(codeFetcher: TrackingProtectionWaitlistCodeFetcher): LifecycleObserver = codeFetcher
}
