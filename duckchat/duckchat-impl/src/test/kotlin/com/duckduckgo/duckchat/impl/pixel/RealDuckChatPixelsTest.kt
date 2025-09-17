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

package com.duckduckgo.duckchat.impl.pixel

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_CREATE_NEW_CHAT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_OPEN_HISTORY
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SELECT_FIRST_HISTORY_ITEM
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_FIRST_PROMPT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_PROMPT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_TAP_KEYBOARD_RETURN_KEY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_KEYBOARD_RETURN_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN_HISTORY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN_MOST_RECENT_HISTORY_CHAT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_START_NEW_CONVERSATION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealDuckChatPixelsTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    private val mockDuckChatFeatureRepository: DuckChatFeatureRepository = mock()

    private lateinit var testee: RealDuckChatPixels

    @Before
    fun setup() = runTest {
        whenever(mockDuckChatFeatureRepository.sessionDeltaInMinutes()).thenReturn(1)

        testee = RealDuckChatPixels(
            pixel = mockPixel,
            duckChatFeatureRepository = mockDuckChatFeatureRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when sendReportMetricPixel with USER_DID_SUBMIT_PROMPT then fires correct pixel with session params`() = runTest {
        whenever(mockDuckChatFeatureRepository.sessionDeltaInMinutes()).thenReturn(5)

        testee.sendReportMetricPixel(USER_DID_SUBMIT_PROMPT)

        advanceUntilIdle()

        verify(mockPixel).fire(
            DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT,
            parameters = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to "5"),
        )
    }

    @Test
    fun `when sendReportMetricPixel with USER_DID_SUBMIT_FIRST_PROMPT then fires correct pixel with session params`() = runTest {
        whenever(mockDuckChatFeatureRepository.sessionDeltaInMinutes()).thenReturn(10)

        testee.sendReportMetricPixel(USER_DID_SUBMIT_FIRST_PROMPT)

        advanceUntilIdle()

        verify(mockPixel).fire(
            DUCK_CHAT_START_NEW_CONVERSATION,
            parameters = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to "10"),
        )
    }

    @Test
    fun `when sendReportMetricPixel with USER_DID_OPEN_HISTORY then fires correct pixel with session params`() = runTest {
        whenever(mockDuckChatFeatureRepository.sessionDeltaInMinutes()).thenReturn(15)

        testee.sendReportMetricPixel(USER_DID_OPEN_HISTORY)

        advanceUntilIdle()

        verify(mockPixel).fire(
            DUCK_CHAT_OPEN_HISTORY,
            parameters = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to "15"),
        )
    }

    @Test
    fun `when sendReportMetricPixel with USER_DID_SELECT_FIRST_HISTORY_ITEM then fires correct pixel with session params`() = runTest {
        whenever(mockDuckChatFeatureRepository.sessionDeltaInMinutes()).thenReturn(20)

        testee.sendReportMetricPixel(USER_DID_SELECT_FIRST_HISTORY_ITEM)

        advanceUntilIdle()

        verify(mockPixel).fire(
            DUCK_CHAT_OPEN_MOST_RECENT_HISTORY_CHAT,
            parameters = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to "20"),
        )
    }

    @Test
    fun `when sendReportMetricPixel with USER_DID_CREATE_NEW_CHAT then fires correct pixel with session params`() = runTest {
        whenever(mockDuckChatFeatureRepository.sessionDeltaInMinutes()).thenReturn(25)

        testee.sendReportMetricPixel(USER_DID_CREATE_NEW_CHAT)

        advanceUntilIdle()

        verify(mockPixel).fire(
            DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED,
            parameters = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to "25"),
        )
    }

    @Test
    fun `when sendReportMetricPixel with USER_DID_TAP_KEYBOARD_RETURN_KEY then fires correct pixel with empty params`() = runTest {
        testee.sendReportMetricPixel(USER_DID_TAP_KEYBOARD_RETURN_KEY)

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_KEYBOARD_RETURN_PRESSED, parameters = emptyMap())
    }
}
