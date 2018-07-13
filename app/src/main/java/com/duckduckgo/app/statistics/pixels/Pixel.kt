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

package com.duckduckgo.app.statistics.pixels

import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.PixelService
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

interface Pixel {

    enum class PixelDefinition(val pixelName: String) {

        // TODO Q: does BrowserActivity launchNewSearchOrQuery only get executed when launched externally?
        // ie can I rely on that being a form of "app launch" ?
        APP_LAUNCH("ml"),
        FORGET_ALL_EXECUTED("mf"),
        PRIVACY_DASHBOARD_OPENED("mp")

    }

    fun fire(pixel: PixelDefinition)

}

class ApiBasedPixel @Inject constructor(private val api: PixelService, private val statisticsDataStore: StatisticsDataStore, private val variantManager: VariantManager) : Pixel {

    override fun fire(pixel: Pixel.PixelDefinition) {

        val atb = statisticsDataStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""

        api.fire(pixel.pixelName, atb)
                .subscribeOn(Schedulers.io())
                .subscribe({
                    Timber.v("Pixel sent: ${pixel.pixelName}")
                }, {
                    Timber.w("Pixel failed: ${pixel.pixelName}", it)
                })

    }

}
