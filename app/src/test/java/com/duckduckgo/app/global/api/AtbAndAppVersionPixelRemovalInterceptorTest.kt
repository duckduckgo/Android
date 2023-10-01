/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.api

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.plugins.pixel.PixelRequiringDataCleaningPlugin
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AtbAndAppVersionPixelRemovalInterceptorTest {
    private lateinit var pixelRemovalInterceptor: AtbAndAppVersionPixelRemovalInterceptor

    @Before
    fun setup() {
        pixelRemovalInterceptor = AtbAndAppVersionPixelRemovalInterceptor(
            pixelsPlugin = object : PluginPoint<PixelRequiringDataCleaningPlugin> {
                override fun getPlugins(): Collection<PixelRequiringDataCleaningPlugin> {
                    return listOf(PixelInterceptorPixelsRequiringDataCleaning)
                }
            },
        )
    }

    @Test
    fun whenSendPixelTheRedactAtvInfoFromDefinedPixels() {
        val appPixelNames = AppPixelName.values().map { it.pixelName }
        val deviceShieldPixelNames = DeviceShieldPixelNames.values().map { it.pixelName }

        (appPixelNames + deviceShieldPixelNames).forEach { pixelName ->
            val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)
            val removalExpected = PixelInterceptorPixelsRequiringDataCleaning.names().firstOrNull { pixelName.startsWith(it) } != null

            val interceptedUrl = pixelRemovalInterceptor.intercept(FakeChain(pixelUrl)).request.url
            assertEquals(removalExpected, interceptedUrl.queryParameter("atb") == null)
            assertEquals(removalExpected, interceptedUrl.queryParameter("appVersion") == null)
        }
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s_android_phone?atb=v255-7zu&appVersion=5.74.0&test=1"
    }
}
