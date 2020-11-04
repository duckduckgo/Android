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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class PixelSender @Inject constructor(
    private val api: PixelService,
    private val pixelDao: PixelDao,
    private val statisticsDataStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val deviceInfo: DeviceInfo
) : LifecycleObserver {

    private val compositeDisposable = CompositeDisposable()
    private var syncJob: Job? = null

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        syncJob = GlobalScope.launch {
            pixelDao.pixels().collect { list ->
                compositeDisposable.clear()
                Timber.i("Pixel sending: $list")
                list.forEach { pixelEntity ->
                    compositeDisposable.add(
                        sendPixel(pixelEntity)
                            .subscribeOn(Schedulers.io())
                            .subscribe({
                                pixelDao.delete(pixelEntity)
                                Timber.v("Pixel sent: ${pixelEntity.pixelName} with params: temp temp)")
                            }, {
                                Timber.w(it, "Pixel failed: ${pixelEntity.pixelName} with params:  temp temp)")
                            })
                    )
                }

            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        syncJob?.cancel()
        compositeDisposable.clear()
    }

    fun sendPixel(pixelName: String, parameters: Map<String, String> = emptyMap(), encodedParameters: Map<String, String> = emptyMap()): Completable {
        return api.fire(pixelName, getDeviceFactor(), getAtbInfo(), addDeviceParametersTo(parameters), encodedParameters)
    }

    fun enqueuePixel(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>): Completable {
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
    
    private fun addDeviceParametersTo(parameters: Map<String, String>): Map<String, String> {
        val defaultParameters = mapOf(Pixel.PixelParameter.APP_VERSION to deviceInfo.appVersion)
        return defaultParameters.plus(parameters)
    }

    private fun getAtbInfo() = statisticsDataStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""

    private fun getDeviceFactor() = deviceInfo.formFactor().description
}
