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

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.global.exception.UncaughtExceptionEntity
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.EXCEPTION_APP_VERSION
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.EXCEPTION_MESSAGE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.EXCEPTION_TIMESTAMP
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.nhaarman.mockitokotlin2.*
import io.reactivex.Completable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class OfflinePixelSenderTest {

    private var mockOfflinePixelCountDataStore: OfflinePixelCountDataStore = mock()
    private var mockUncaughtExceptionRepository: UncaughtExceptionRepository = mock()
    private var mockPixel: PixelSender = mock()

    private var testee: OfflinePixelSender = OfflinePixelSender(mockOfflinePixelCountDataStore, mockUncaughtExceptionRepository, mockPixel)

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Before
    fun before() {
        val exceptionEntity = UncaughtExceptionEntity(1, UncaughtExceptionSource.GLOBAL, "test", "version", 1588167165000)

        runBlocking<Unit> {
            whenever(mockPixel.sendPixel(any(), any(), any())).thenReturn(Completable.complete())
            whenever(mockUncaughtExceptionRepository.getExceptions()).thenReturn(listOf(exceptionEntity))
        }
    }

    @Test
    fun whenSendUncaughtExceptionsPixelThenTimestampFormattedToUtc() {
        val params = mapOf(
            EXCEPTION_MESSAGE to "test",
            EXCEPTION_APP_VERSION to "version",
            EXCEPTION_TIMESTAMP to "2020-04-29T13:32:45+0000"
        )

        testee.sendOfflinePixels().blockingAwait()

        verify(mockPixel).sendPixel(Pixel.AppPixelName.APPLICATION_CRASH_GLOBAL.pixelName, params, emptyMap())
    }
}
