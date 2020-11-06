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

package com.duckduckgo.app.statistics.api

import androidx.annotation.UiThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.model.PixelEntity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.PixelDao
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

interface PixelSender : LifecycleObserver {
    fun sendPixel(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>): Completable
    fun enqueuePixel(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>): Completable
}

class RxPixelSender @Inject constructor(
    private val api: PixelService,
    private val pixelDao: PixelDao,
    private val statisticsDataStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val deviceInfo: DeviceInfo
) : PixelSender {

    private val compositeDisposable = CompositeDisposable()

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        compositeDisposable.add(pixelDao.unsentPixels()
            .flatMapIterable { it }
            .switchMapCompletable(this::sendAndDeletePixel)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { Timber.v("Pixel finished sync") },
                { Timber.w(it, "Pixel failed to sync") }
            ))
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        compositeDisposable.clear()
    }

    private fun sendAndDeletePixel(pixel: PixelEntity): Completable {
        return sendPixel(pixel)
            .andThen(deletePixel(pixel))
            .andThen {
                with(pixel) {
                    Timber.i("Pixel sent: $id $pixelName with params: $additionalQueryParams $encodedQueryParams")
                }
            }
            .doOnError {
                with(pixel) {
                    Timber.i("Pixel failed: $id $pixelName with params: $additionalQueryParams $encodedQueryParams")
                }
            }.onErrorComplete()
    }

    override fun sendPixel(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>): Completable {
        return api.fire(pixelName, getDeviceFactor(), getAtbInfo(), addDeviceParametersTo(parameters), encodedParameters)
    }

    override fun enqueuePixel(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>): Completable {
        return Completable.fromCallable {
            val pixelEntity = PixelEntity(
                pixelName = pixelName,
                atb = getAtbInfo(),
                additionalQueryParams = parameters,
                encodedQueryParams = encodedParameters
            )
            pixelDao.insert(pixelEntity)
        }
    }

    private fun sendPixel(pixelEntity: PixelEntity): Completable {
        with(pixelEntity) {
            return api.fire(
                this.pixelName,
                getDeviceFactor(),
                this.atb,
                addDeviceParametersTo(this.additionalQueryParams),
                this.encodedQueryParams
            )
        }
    }

    private fun deletePixel(pixel: PixelEntity): Completable {
        return Completable.fromAction {
            pixelDao.delete(pixel)
        }
    }

    private fun addDeviceParametersTo(parameters: Map<String, String>): Map<String, String> {
        val defaultParameters = mapOf(Pixel.PixelParameter.APP_VERSION to deviceInfo.appVersion)
        return defaultParameters.plus(parameters)
    }

    private fun getAtbInfo() = statisticsDataStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""

    private fun getDeviceFactor() = deviceInfo.formFactor().description
}
