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

import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.pixels.AppPixelName.PRIVACY_DASHBOARD_OPENED
import com.duckduckgo.app.statistics.api.PixelSender
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Completable
import java.util.concurrent.TimeoutException
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock

class RxBasedPixelTest {

    @get:Rule @Suppress("unused") val schedulers = InstantSchedulersRule()

    @Mock val mockPixelSender = mock<PixelSender>()

    @Test
    fun whenPixelWithoutQueryParamsFiredThenPixelSentWithDefaultParams() {
        givenSendPixelSucceeds()

        val pixel = RxBasedPixel(mockPixelSender)
        pixel.fire(PRIVACY_DASHBOARD_OPENED)

        verify(mockPixelSender).sendPixel("mp", emptyMap(), emptyMap())
    }

    @Test
    fun whenPixelWithoutQueryParamsFiredFailsThenErrorHandled() {
        givenSendPixelFails()

        val pixel = RxBasedPixel(mockPixelSender)
        pixel.fire(PRIVACY_DASHBOARD_OPENED)

        verify(mockPixelSender).sendPixel("mp", emptyMap(), emptyMap())
    }

    @Test
    fun whenPixelWithQueryParamsFiredThenPixelSentWithParams() {
        givenSendPixelSucceeds()

        val pixel = RxBasedPixel(mockPixelSender)
        val params = mapOf("param1" to "value1", "param2" to "value2")

        pixel.fire(PRIVACY_DASHBOARD_OPENED, params)
        verify(mockPixelSender).sendPixel("mp", params, emptyMap())
    }

    @Test
    fun whenPixelWithoutQueryParamsEnqueuedThenPixelEnqueuedWithDefaultParams() {
        givenEnqueuePixelSucceeds()

        val pixel = RxBasedPixel(mockPixelSender)
        pixel.enqueueFire(PRIVACY_DASHBOARD_OPENED)

        verify(mockPixelSender).enqueuePixel("mp", emptyMap(), emptyMap())
    }

    @Test
    fun whenPixelWithoutQueryParamsEnqueuedThenErrorHandled() {
        givenEnqueuePixelFails()

        val pixel = RxBasedPixel(mockPixelSender)
        pixel.enqueueFire(PRIVACY_DASHBOARD_OPENED)

        verify(mockPixelSender).enqueuePixel("mp", emptyMap(), emptyMap())
    }

    @Test
    fun whenPixelWithQueryParamsEnqueuedThenPixelEnqueuedWithParams() {
        givenEnqueuePixelSucceeds()

        val pixel = RxBasedPixel(mockPixelSender)
        val params = mapOf("param1" to "value1", "param2" to "value2")
        pixel.enqueueFire(PRIVACY_DASHBOARD_OPENED, params)

        verify(mockPixelSender).enqueuePixel("mp", params, emptyMap())
    }

    private fun givenEnqueuePixelSucceeds() {
        whenever(mockPixelSender.enqueuePixel(any(), any(), any()))
            .thenReturn(Completable.complete())
    }

    private fun givenEnqueuePixelFails() {
        whenever(mockPixelSender.enqueuePixel(any(), any(), any()))
            .thenReturn(Completable.error(TimeoutException()))
    }

    private fun givenSendPixelSucceeds() {
        whenever(mockPixelSender.sendPixel(any(), any(), any())).thenReturn(Completable.complete())
    }

    private fun givenSendPixelFails() {
        whenever(mockPixelSender.sendPixel(any(), any(), any()))
            .thenReturn(Completable.error(TimeoutException()))
    }
}
