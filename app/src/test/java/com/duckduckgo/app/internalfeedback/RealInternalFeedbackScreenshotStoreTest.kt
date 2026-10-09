/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.internalfeedback

import android.graphics.Bitmap
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

class RealInternalFeedbackScreenshotStoreTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    // Holds back encoding until resumed, so tests can observe the store while a screenshot is still encoding.
    private val encodingDispatcher = PausedDispatcher()

    private val store = createStore(coroutineTestRule.testDispatcherProvider)

    private val storeWithPausedEncoding = createStore(
        object : DispatcherProvider by coroutineTestRule.testDispatcherProvider {
            override fun computation(): CoroutineDispatcher = encodingDispatcher
        },
    )

    @Test
    fun whenNothingStoredThenNoScreenshotIsReturned() = runTest {
        assertNull(store.takeEncodedScreenshot())
    }

    @Test
    fun whenScreenshotStoredThenItIsReturnedAsBase64PngOnce() = runTest {
        store.put(screenshot(PNG))

        assertEquals(PNG_BASE64, store.takeEncodedScreenshot())
        assertNull(store.takeEncodedScreenshot())
    }

    @Test
    fun whenScreenshotEncodedThenBitmapIsRecycled() = runTest {
        val screenshot = screenshot(PNG)

        store.put(screenshot)

        verify(screenshot).recycle()
    }

    @Test
    fun whenEncodingFailsThenNoScreenshotIsReturned() = runTest {
        val screenshot: Bitmap = mock { on { compress(any(), any(), any()) } doReturn false }
        store.put(screenshot)

        assertNull(store.takeEncodedScreenshot())
        verify(screenshot).recycle()
    }

    @Test
    fun whenExpiryTimerElapsesThenScreenshotIsClearedAndEncodingIsCancelled() = runTest {
        val screenshot = screenshot(PNG)
        storeWithPausedEncoding.put(screenshot)

        testScheduler.advanceTimeBy(MINUTES_5_MS + 1)

        encodingDispatcher.resume()
        verify(screenshot, never()).compress(any(), any(), any())
        assertNull(storeWithPausedEncoding.takeEncodedScreenshot())
    }

    @Test
    fun whenNewScreenshotStoredThenExpiryTimerRestarts() = runTest {
        store.put(screenshot(OTHER_PNG))
        testScheduler.advanceTimeBy(MINUTES_4_MS)
        store.put(screenshot(PNG))

        testScheduler.advanceTimeBy(MINUTES_2_MS)

        assertEquals(PNG_BASE64, store.takeEncodedScreenshot())
    }

    @Test
    fun whenScreenshotTakenThenItsExpiryTimerDoesNotClearANewerOne() = runTest {
        store.put(screenshot(OTHER_PNG))
        store.takeEncodedScreenshot()
        testScheduler.advanceTimeBy(MINUTES_4_MS)
        store.put(screenshot(PNG))

        testScheduler.advanceTimeBy(MINUTES_2_MS)

        assertEquals(PNG_BASE64, store.takeEncodedScreenshot())
    }

    @Test
    fun whenNewScreenshotStoredThenItReplacesThePreviousOne() = runTest {
        val previousScreenshot = screenshot(OTHER_PNG)
        storeWithPausedEncoding.put(previousScreenshot)

        storeWithPausedEncoding.put(screenshot(PNG))
        encodingDispatcher.resume()

        verify(previousScreenshot, never()).compress(any(), any(), any())
        assertEquals(PNG_BASE64, storeWithPausedEncoding.takeEncodedScreenshot())
    }

    @Test
    fun whenScreenshotStillEncodingThenTakingItWaitsForEncoding() = runTest {
        storeWithPausedEncoding.put(screenshot(PNG))

        val taken = async { storeWithPausedEncoding.takeEncodedScreenshot() }
        testScheduler.runCurrent()
        assertFalse(taken.isCompleted)

        encodingDispatcher.resume()
        assertEquals(PNG_BASE64, taken.await())
    }

    private fun createStore(dispatcherProvider: DispatcherProvider) = RealInternalFeedbackScreenshotStore(
        appCoroutineScope = coroutineTestRule.testScope,
        dispatcherProvider = dispatcherProvider,
    )

    private fun screenshot(png: ByteArray): Bitmap = mock {
        on { compress(any(), any(), any()) } doAnswer {
            it.getArgument<OutputStream>(2).write(png)
            true
        }
    }

    private class PausedDispatcher : CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()
        private var paused = true

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = paused

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending.addLast(block)
        }

        fun resume() {
            paused = false
            while (pending.isNotEmpty()) pending.removeFirst().run()
        }
    }

    private companion object {
        val PNG = byteArrayOf(1, 2, 3)
        const val PNG_BASE64 = "AQID"
        val OTHER_PNG = byteArrayOf(4, 5, 6)
        const val MINUTES_2_MS = 2 * 60 * 1000L
        const val MINUTES_4_MS = 4 * 60 * 1000L
        const val MINUTES_5_MS = 5 * 60 * 1000L
    }
}
