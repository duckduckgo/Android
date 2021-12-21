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

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PixelReQueryInterceptorTest {

    private lateinit var pixelReQueryInterceptor: PixelReQueryInterceptor

    @Before
    fun setup() {
        pixelReQueryInterceptor = PixelReQueryInterceptor()
    }

    @Test
    fun whenRq0PixelIsSendThenRemoveDeviceAndFormFactor() {
        assertEquals(
            EXPECTED_RQ_0_URL.toHttpUrl(),
            pixelReQueryInterceptor.intercept(FakeChain(RQ_0_PHONE_URL)).request.url)

        assertEquals(
            EXPECTED_RQ_0_URL.toHttpUrl(),
            pixelReQueryInterceptor.intercept(FakeChain(RQ_0_TABLET_URL)).request.url)
    }

    @Test
    fun whenRq1PixelIsSendThenRemoveDeviceAndFormFactor() {
        assertEquals(
            EXPECTED_RQ_1_URL.toHttpUrl(),
            pixelReQueryInterceptor.intercept(FakeChain(RQ_1_PHONE_URL)).request.url)

        assertEquals(
            EXPECTED_RQ_1_URL.toHttpUrl(),
            pixelReQueryInterceptor.intercept(FakeChain(RQ_1_TABLET_URL)).request.url)
    }

    @Test
    fun whenPixelOtherThanRqIsSendThenDoNotModify() {
        assertEquals(
            EXPECTED_OTHER_PIXEL_PHONE_URL.toHttpUrl(),
            pixelReQueryInterceptor.intercept(FakeChain(OTHER_PIXEL_PHONE_URL)).request.url)

        assertEquals(
            EXPECTED_OTHER_PIXEL_TABLET_URL.toHttpUrl(),
            pixelReQueryInterceptor.intercept(FakeChain(OTHER_PIXEL_TABLET_URL)).request.url)
    }

    private companion object {
        private const val RQ_0_PHONE_URL =
            "https://improving.duckduckgo.com/t/rq_0_android_phone?atb=v255-7zu&appVersion=5.74.0&test=1"
        private const val RQ_0_TABLET_URL =
            "https://improving.duckduckgo.com/t/rq_0_android_tablet?atb=v255-7zu&appVersion=5.74.0&test=1"
        private const val RQ_1_PHONE_URL =
            "https://improving.duckduckgo.com/t/rq_1_android_phone?atb=v255-7zu&appVersion=5.74.0&test=1"
        private const val RQ_1_TABLET_URL =
            "https://improving.duckduckgo.com/t/rq_1_android_tablet?atb=v255-7zu&appVersion=5.74.0&test=1"
        private const val OTHER_PIXEL_PHONE_URL =
            "https://improving.duckduckgo.com/t/my_pixel_android_phone?atb=v255-7zu&appVersion=5.74.0&test=1"
        private const val OTHER_PIXEL_TABLET_URL =
            "https://improving.duckduckgo.com/t/my_pixel_android_tablet?atb=v255-7zu&appVersion=5.74.0&test=1"

        private const val EXPECTED_RQ_0_URL =
            "https://improving.duckduckgo.com/t/rq_0?atb=v255-7zu&appVersion=5.74.0&test=1"
        private const val EXPECTED_RQ_1_URL =
            "https://improving.duckduckgo.com/t/rq_1?atb=v255-7zu&appVersion=5.74.0&test=1"
        private const val EXPECTED_OTHER_PIXEL_PHONE_URL =
            "https://improving.duckduckgo.com/t/my_pixel_android_phone?atb=v255-7zu&appVersion=5.74.0&test=1"
        private const val EXPECTED_OTHER_PIXEL_TABLET_URL =
            "https://improving.duckduckgo.com/t/my_pixel_android_tablet?atb=v255-7zu&appVersion=5.74.0&test=1"
    }
}
