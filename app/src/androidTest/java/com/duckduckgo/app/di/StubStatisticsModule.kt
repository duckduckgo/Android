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
import com.duckduckgo.app.global.device.ContextDeviceInfo
import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.statistics.AtbInitializer
import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.app.statistics.api.PixelSender
import com.duckduckgo.app.statistics.api.StatisticsService
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import io.reactivex.Completable
import kotlinx.coroutines.CoroutineScope
import retrofit2.Retrofit
import dagger.SingleInstanceIn

@Module
@ContributesTo(
    scope = AppObjectGraph::class,
    replaces = [StatisticsModule::class]
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

        }
    }

    @Provides
    fun stubPixel(): Pixel {
        return object : Pixel {

            override fun fire(pixel: Pixel.PixelName, parameters: Map<String, String>, encodedParameters: Map<String, String>) {
            }

            override fun fire(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>) {

            }

            override fun enqueueFire(pixel: Pixel.PixelName, parameters: Map<String, String>, encodedParameters: Map<String, String>) {

            }

            override fun enqueueFire(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>) {

            }
        }
    }

    @Provides
    fun deviceInfo(context: Context): DeviceInfo = ContextDeviceInfo(context)

    @Provides
    @IntoSet
    @SingleInstanceIn(AppObjectGraph::class)
    fun atbInitializer(
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        statisticsDataStore: StatisticsDataStore,
        statisticsUpdater: StatisticsUpdater,
        listeners: Set<@JvmSuppressWildcards AtbInitializerListener>
    ): LifecycleObserver {
        return AtbInitializer(appCoroutineScope, statisticsDataStore, statisticsUpdater, listeners)
    }

    @Provides
    fun pixelSender(): PixelSender {
        return object : PixelSender {
            override fun sendPixel(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>): Completable {
                return Completable.fromAction {}
            }

            override fun enqueuePixel(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>): Completable {
                return Completable.fromAction {}
            }

        }
    }
}
