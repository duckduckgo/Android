/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.duckchat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_VISIBLE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealDuckChatOmnibarImpressionPixelSenderTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val pixel: Pixel = mock()

    private lateinit var sender: DuckChatOmnibarImpressionPixelSender

    @Before
    fun setUp() {
        sender = RealDuckChatOmnibarImpressionPixelSender(
            pixel = pixel,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun whenVisibleForDelayTimeThenPixelFiresOnce() = runTest {
        sender.sendImpressionPixel(true)
        advanceTimeBy(300)
        runCurrent()

        verify(pixel, times(1)).fire(DUCK_CHAT_SEARCHBAR_BUTTON_VISIBLE.pixelName)
    }

    @Test
    fun whenHiddenBeforeDelayTimeThenPixelDoesNotFire() = runTest {
        sender.sendImpressionPixel(true)
        advanceTimeBy(150)

        sender.sendImpressionPixel(false)
        advanceTimeBy(300)
        runCurrent()

        verify(pixel, never()).fire(DUCK_CHAT_SEARCHBAR_BUTTON_VISIBLE.pixelName)
    }

    @Test
    fun whenVisibilityResetsThenTimerRestartsAndPixelFiresOnce() = runTest {
        sender.sendImpressionPixel(true)
        advanceTimeBy(150)

        sender.sendImpressionPixel(true)
        advanceTimeBy(300)
        runCurrent()

        verify(pixel, times(1)).fire(DUCK_CHAT_SEARCHBAR_BUTTON_VISIBLE.pixelName)
    }
}
