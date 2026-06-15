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

import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.helper.DuckChatTermsOfServiceHandler
import com.duckduckgo.duckchat.impl.metric.DuckAiMetricCollector
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RealDuckChatPixelsAttachmentsTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val statisticsUpdater: StatisticsUpdater = mock()
    private val duckAiMetricCollector: DuckAiMetricCollector = mock()
    private val termsOfServiceHandler: DuckChatTermsOfServiceHandler = mock()

    private val testee = RealDuckChatPixels(
        pixel = pixel,
        duckChatFeatureRepository = duckChatFeatureRepository,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        statisticsUpdater = statisticsUpdater,
        duckAiMetricCollector = duckAiMetricCollector,
        termsOfServiceHandler = termsOfServiceHandler,
    )

    @Test
    fun whenImageAttachedFromCameraThenSourceParam() = runTest {
        testee.fireImageAttached(source = "camera")

        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_ATTACHED_COUNT,
            parameters = mapOf(DuckChatPixelParameters.ATTACHMENT_SOURCE to "camera"),
        )
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_ATTACHED_DAILY,
            parameters = mapOf(DuckChatPixelParameters.ATTACHMENT_SOURCE to "camera"),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenImageRemovedThenCountAndDaily() = runTest {
        testee.fireImageRemoved()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_REMOVED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_REMOVED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenImageValidationFailedThenReasonParam() = runTest {
        testee.fireImageValidationFailed(reason = "count_exceeded")

        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_VALIDATION_FAILED_COUNT,
            parameters = mapOf(DuckChatPixelParameters.FILE_VALIDATION_REASON to "count_exceeded"),
        )
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_VALIDATION_FAILED_DAILY,
            parameters = mapOf(DuckChatPixelParameters.FILE_VALIDATION_REASON to "count_exceeded"),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenFileAttachedThenCountAndDaily() = runTest {
        testee.fireFileAttached()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_ATTACHED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_ATTACHED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenFileRemovedThenCountAndDaily() = runTest {
        testee.fireFileRemoved()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_REMOVED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_REMOVED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenFileValidationFailedThenReasonParam() = runTest {
        testee.fireFileValidationFailed(reason = "size_exceeded")

        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_VALIDATION_FAILED_COUNT,
            parameters = mapOf(DuckChatPixelParameters.FILE_VALIDATION_REASON to "size_exceeded"),
        )
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_FILE_VALIDATION_FAILED_DAILY,
            parameters = mapOf(DuckChatPixelParameters.FILE_VALIDATION_REASON to "size_exceeded"),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenVoiceTappedThenCountAndDaily() = runTest {
        testee.fireVoiceTapped()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_VOICE_TAPPED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_VOICE_TAPPED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenStopTappedThenSingleCount() = runTest {
        testee.fireStopGenerationTapped()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_STOP_GENERATION_TAPPED)
    }
}
