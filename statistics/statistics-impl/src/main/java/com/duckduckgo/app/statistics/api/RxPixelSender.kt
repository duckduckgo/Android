/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.statistics.api

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.config.StatisticsLibraryConfig
import com.duckduckgo.app.statistics.model.PixelEntity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.statistics.store.PendingPixelDao
import com.duckduckgo.app.statistics.store.PixelFiredRepository
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(
    scope = AppScope::class,
    boundType = PixelSender::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class RxPixelSender @Inject constructor(
    private val api: PixelService,
    private val pendingPixelDao: PendingPixelDao,
    private val statisticsDataStore: StatisticsDataStore,
    private val deviceInfo: DeviceInfo,
    private val statisticsLibraryConfig: StatisticsLibraryConfig?,
    private val pixelFiredRepository: PixelFiredRepository,
) : PixelSender, MainProcessLifecycleObserver {

    private val compositeDisposable = CompositeDisposable()

    private val shouldFirePixelsAsDev: Int? by lazy {
        if (statisticsLibraryConfig?.shouldFirePixelsAsDev() == true) 1 else null
    }

    override fun onStart(owner: LifecycleOwner) {
        compositeDisposable.add(
            pendingPixelDao.pixels()
                .flatMapIterable { it }
                .switchMapCompletable(this::sendAndDeletePixel)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { logcat(VERBOSE) { "Pixel finished sync" } },
                    { logcat(WARN) { "Pixel failed to sync: ${it.asLog()}" } },
                ),
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        compositeDisposable.clear()
    }

    private fun sendAndDeletePixel(pixel: PixelEntity): Completable {
        return sendPixel(pixel)
            .andThen(deletePixel(pixel))
            .andThen {
                with(pixel) {
                    logcat(INFO) { "Pixel sent: $id $pixelName with params: $additionalQueryParams $encodedQueryParams" }
                }
            }
            .doOnError {
                with(pixel) {
                    logcat(INFO) { "Pixel failed: $id $pixelName with params: $additionalQueryParams $encodedQueryParams" }
                }
            }.onErrorComplete()
    }

    override fun sendPixel(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: Pixel.PixelType,
    ): Single<PixelSender.SendPixelResult> = Single.fromCallable {
        runBlocking {
            if (shouldFirePixel(pixelName, type)) {
                api.fire(
                    pixelName,
                    getDeviceFactor(),
                    getAtbInfo(),
                    addDeviceParametersTo(parameters),
                    encodedParameters,
                    devMode = shouldFirePixelsAsDev,
                ).blockingAwait()
                storePixelFired(pixelName, type)
                PixelSender.SendPixelResult.PIXEL_SENT
            } else {
                PixelSender.SendPixelResult.PIXEL_IGNORED
            }
        }
    }

    override fun enqueuePixel(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
    ): Completable {
        return Completable.fromCallable {
            val pixelEntity = PixelEntity(
                pixelName = pixelName,
                atb = getAtbInfo(),
                additionalQueryParams = addDeviceParametersTo(parameters),
                encodedQueryParams = encodedParameters,
            )
            pendingPixelDao.insert(pixelEntity)
        }
    }

    private fun sendPixel(pixelEntity: PixelEntity): Completable {
        with(pixelEntity) {
            return api.fire(
                this.pixelName,
                getDeviceFactor(),
                this.atb,
                this.additionalQueryParams,
                this.encodedQueryParams,
                devMode = shouldFirePixelsAsDev,
            )
        }
    }

    private fun deletePixel(pixel: PixelEntity): Completable {
        return Completable.fromAction {
            pendingPixelDao.delete(pixel)
        }
    }

    private fun addDeviceParametersTo(parameters: Map<String, String>): Map<String, String> {
        val defaultParameters = mapOf(Pixel.PixelParameter.APP_VERSION to deviceInfo.appVersion)
        return defaultParameters.plus(parameters)
    }

    private fun getAtbInfo() = statisticsDataStore.atb?.formatWithVariant(statisticsDataStore.variant) ?: ""

    private fun getDeviceFactor() = deviceInfo.formFactor().description

    private suspend fun shouldFirePixel(
        pixelName: String,
        type: Pixel.PixelType,
    ): Boolean =
        when (type) {
            is Count -> true
            is Daily -> !pixelFiredRepository.hasDailyPixelFiredToday(type.tag ?: pixelName)
            is Unique -> !pixelFiredRepository.hasUniquePixelFired(type.tag ?: pixelName)
        }

    private suspend fun storePixelFired(
        pixelName: String,
        type: Pixel.PixelType,
    ) {
        when (type) {
            is Count -> {} // no-op
            is Daily -> pixelFiredRepository.storeDailyPixelFiredToday(type.tag ?: pixelName)
            is Unique -> pixelFiredRepository.storeUniquePixelFired(type.tag ?: pixelName)
        }
    }
}
