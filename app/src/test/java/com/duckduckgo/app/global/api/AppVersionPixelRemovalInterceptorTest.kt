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

import com.duckduckgo.app.pixels.AppPixelName
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AppVersionPixelRemovalInterceptorTest {
    private lateinit var appVersionPixelRemovalInterceptor: AppVersionPixelRemovalInterceptor

    @Before
    fun setup() {
        appVersionPixelRemovalInterceptor = AppVersionPixelRemovalInterceptor()
    }

    @Test
    fun whenSendPixelTheRedactAppVersionFromDefinedPixels() {
        AppPixelName.values().map { it.pixelName }.forEach { pixelName ->
            val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)
            val removalExpected = AppVersionPixelRemovalInterceptor.pixel_prefixes.firstOrNull { pixelName.startsWith(it) } != null

            val interceptedUrl = appVersionPixelRemovalInterceptor.intercept(FakeChain(pixelUrl)).request.url
            Assert.assertEquals(removalExpected, interceptedUrl.queryParameter("appVersion") == null)
        }
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s_android_phone?atb=v255-7zu&appVersion=5.74.0&test=1"
    }
}
