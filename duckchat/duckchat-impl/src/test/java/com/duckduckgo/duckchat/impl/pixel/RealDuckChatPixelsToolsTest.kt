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

class RealDuckChatPixelsToolsTest {

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
    fun whenImageGenerationSelectedThenFiresCountAndDaily() = runTest {
        testee.fireImageGenerationSelected()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SELECTED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SELECTED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenImageGenerationDeselectedThenFiresCountAndDaily() = runTest {
        testee.fireImageGenerationDeselected()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_DESELECTED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_DESELECTED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenImageGenerationSubmittedThenFiresCountAndDaily() = runTest {
        testee.fireImageGenerationSubmitted()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SUBMITTED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SUBMITTED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenWebSearchSelectedThenFiresCountAndDaily() = runTest {
        testee.fireWebSearchSelected()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SELECTED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SELECTED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenWebSearchDeselectedThenFiresCountAndDaily() = runTest {
        testee.fireWebSearchDeselected()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_DESELECTED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_DESELECTED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenWebSearchSubmittedThenFiresCountAndDaily() = runTest {
        testee.fireWebSearchSubmitted()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SUBMITTED_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SUBMITTED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenPromptSubmittedThenFiresWithFullState() = runTest {
        testee.firePromptSubmitted(
            selectedTool = "image_generation",
            modelId = "gpt-5",
            reasoningEffort = "fast",
            hasImageAttachment = true,
            hasFileAttachment = false,
            hasText = true,
        )

        val params = mapOf(
            DuckChatPixelParameters.SELECTED_TOOL to "image_generation",
            DuckChatPixelParameters.MODEL_ID to "gpt-5",
            DuckChatPixelParameters.REASONING_EFFORT to "fast",
            DuckChatPixelParameters.HAS_IMAGE_ATTACHMENT to "true",
            DuckChatPixelParameters.HAS_FILE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_TEXT to "true",
        )
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT, parameters = params)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_DAILY,
            parameters = params,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenPromptSubmittedWithNullModelAndReasoningThenOmitsThoseParams() = runTest {
        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
        )

        val params = mapOf(
            DuckChatPixelParameters.SELECTED_TOOL to "none",
            DuckChatPixelParameters.HAS_IMAGE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_FILE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_TEXT to "true",
        )
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT, parameters = params)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_DAILY,
            parameters = params,
            type = Pixel.PixelType.Daily(),
        )
    }
}
