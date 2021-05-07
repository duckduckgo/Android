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
import androidx.lifecycle.LifecycleObserver
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.device.ContextDeviceInfo
import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.statistics.AtbInitializer
import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.*
import com.duckduckgo.app.statistics.pixels.RxBasedPixel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.app.statistics.store.PendingPixelDao
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
class StatisticsModule {

    @Provides
    fun statisticsService(@Named("api") retrofit: Retrofit): StatisticsService = retrofit.create(StatisticsService::class.java)

    @Provides
    fun statisticsUpdater(
        statisticsDataStore: StatisticsDataStore,
        statisticsService: StatisticsService,
        variantManager: VariantManager
    ): StatisticsUpdater =
        StatisticsRequester(statisticsDataStore, statisticsService, variantManager)

    @Provides
    fun pixelService(@Named("nonCaching") retrofit: Retrofit): PixelService {
        return retrofit.create(PixelService::class.java)
    }

    @Provides
    fun pixel(
        pixelSender: PixelSender
    ): Pixel =
        RxBasedPixel(pixelSender)

    @Provides
    @Singleton
    fun pixelSender(
        pixelService: PixelService,
        statisticsDataStore: StatisticsDataStore,
        variantManager: VariantManager,
        deviceInfo: DeviceInfo,
        pendingPixelDao: PendingPixelDao
    ): PixelSender =
        RxPixelSender(pixelService, pendingPixelDao, statisticsDataStore, variantManager, deviceInfo)

    @Provides
    @Singleton
    @IntoSet
    fun pixelSenderObserver(pixelSender: PixelSender): LifecycleObserver = pixelSender

    @Provides
    fun offlinePixelSender(
        offlinePixelCountDataStore: OfflinePixelCountDataStore,
        uncaughtExceptionRepository: UncaughtExceptionRepository,
        pixelSender: PixelSender
    ): OfflinePixelSender = OfflinePixelSender(offlinePixelCountDataStore, uncaughtExceptionRepository, pixelSender)

    @Provides
    fun deviceInfo(context: Context): DeviceInfo = ContextDeviceInfo(context)

    @Provides
    @Singleton
    fun atbInitializer(
        statisticsDataStore: StatisticsDataStore,
        statisticsUpdater: StatisticsUpdater,
        listeners: Set<@JvmSuppressWildcards AtbInitializerListener>
    ): AtbInitializer {
        return AtbInitializer(statisticsDataStore, statisticsUpdater, listeners)
    }

    @Singleton
    @Provides
    fun pixelDao(database: AppDatabase): PendingPixelDao {
        return database.pixelDao()
    }
}
