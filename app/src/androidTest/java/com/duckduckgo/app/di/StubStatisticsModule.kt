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
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.AtbInitializer
import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.app.statistics.api.PixelSender
import com.duckduckgo.app.statistics.api.PixelSender.EnqueuePixelResult
import com.duckduckgo.app.statistics.api.PixelSender.SendPixelResult
import com.duckduckgo.app.statistics.api.PixelSender.SendPixelResult.PIXEL_SENT
import com.duckduckgo.app.statistics.api.StatisticsService
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.ContextDeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import io.reactivex.Single
import kotlinx.coroutines.CoroutineScope
import retrofit2.Retrofit

@Module
@ContributesTo(
    scope = AppScope::class,
    replaces = [StatisticsModule::class],
)
class StubStatisticsModule {

    @Provides
    fun statisticsService(retrofit: Retrofit): StatisticsService =
        retrofit.create(StatisticsService::class.java)

    @Provides
    fun stubStatisticsUpdater(): StatisticsUpdater {
        return object : StatisticsUpdater {

            override fun initializeAtb() {
            }

            override fun refreshAppRetentionAtb() {
            }

            override fun refreshSearchRetentionAtb() {
            }

            override fun refreshDuckAiRetentionAtb() {
            }
        }
    }

    @Provides
    fun stubPixel(): Pixel {
        return object : Pixel {

            override fun fire(
                pixel: Pixel.PixelName,
                parameters: Map<String, String>,
                encodedParameters: Map<String, String>,
                type: PixelType,
            ) {
            }

            override fun fire(
                pixelName: String,
                parameters: Map<String, String>,
                encodedParameters: Map<String, String>,
                type: PixelType,
            ) {
            }

            override fun enqueueFire(
                pixel: Pixel.PixelName,
                parameters: Map<String, String>,
                encodedParameters: Map<String, String>,
                type: PixelType,
            ) {
            }

            override fun enqueueFire(
                pixelName: String,
                parameters: Map<String, String>,
                encodedParameters: Map<String, String>,
                type: PixelType,
            ) {
            }
        }
    }

    @Provides
    fun deviceInfo(context: Context): DeviceInfo = ContextDeviceInfo(context)

    @Provides
    @IntoSet
    @SingleInstanceIn(AppScope::class)
    fun atbInitializer(
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        statisticsDataStore: StatisticsDataStore,
        statisticsUpdater: StatisticsUpdater,
        listeners: PluginPoint<AtbInitializerListener>,
        dispatcherProvider: DispatcherProvider,
        pixel: Pixel,
    ): MainProcessLifecycleObserver {
        return AtbInitializer(appCoroutineScope, statisticsDataStore, statisticsUpdater, listeners, dispatcherProvider, pixel)
    }

    @Provides
    fun pixelSender(): PixelSender {
        return object : PixelSender {
            override fun sendPixel(
                pixelName: String,
                parameters: Map<String, String>,
                encodedParameters: Map<String, String>,
                type: PixelType,
            ): Single<SendPixelResult> {
                return Single.just(PIXEL_SENT)
            }

            override fun enqueuePixel(
                pixelName: String,
                parameters: Map<String, String>,
                encodedParameters: Map<String, String>,
                type: PixelType,
            ): Single<EnqueuePixelResult> {
                return Single.just(EnqueuePixelResult.PIXEL_ENQUEUED)
            }
        }
    }
}
