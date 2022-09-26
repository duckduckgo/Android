/*
 * Copyright (c) 2022 DuckDuckGo
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

import com.duckduckgo.adclick.impl.pixels.AdClickPixelName
import com.duckduckgo.app.global.api.PixelAdClickAttributionRemovalInterceptor.Companion.PIXELS_SET_NO_ATB
import com.duckduckgo.app.global.api.PixelAdClickAttributionRemovalInterceptor.Companion.PIXELS_SET_NO_ATB_AND_VERSION
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PixelAdClickAttributionRemovalInterceptorTest {
    private lateinit var interceptor: PixelAdClickAttributionRemovalInterceptor

    @Before
    fun setup() {
        interceptor = PixelAdClickAttributionRemovalInterceptor()
    }

    @Test
    fun whenSendPixelTheRemoveAtbInfoFromDefinedPixels() {
        AdClickPixelName.values().map { it.pixelName }.forEach { pixelName ->
            val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)
            val removalExpected = PIXELS_SET_NO_ATB.contains(pixelName) || PIXELS_SET_NO_ATB_AND_VERSION.contains(pixelName)

            val interceptedUrl = interceptor.intercept(FakeChain(pixelUrl)).request.url

            assertEquals(removalExpected, interceptedUrl.queryParameter("atb") == null)
        }
    }

    @Test
    fun whenSendPixelTheRemoveAppVersionInfoFromDefinedPixels() {
        AdClickPixelName.values().map { it.pixelName }.forEach { pixelName ->
            val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)
            val removalExpected = PIXELS_SET_NO_ATB_AND_VERSION.contains(pixelName)

            val interceptedUrl = interceptor.intercept(FakeChain(pixelUrl)).request.url

            assertEquals(removalExpected, interceptedUrl.queryParameter("appVersion") == null)
        }
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s_android_phone?atb=v337-7&appVersion=5.132.1&test=1"
    }
}
