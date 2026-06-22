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

class RealDuckChatPixelsPickerTest {

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
    fun whenModelSelectedThenSingleCountWithModelId() = runTest {
        testee.fireModelSelected(modelId = "gpt-5")
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_MODEL_SELECTED,
            parameters = mapOf(DuckChatPixelParameters.MODEL_ID to "gpt-5"),
        )
    }

    @Test
    fun whenReasoningSelectedThenSingleCountWithEffort() = runTest {
        testee.fireReasoningEffortSelected(effortLevel = "extended_reasoning")
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_REASONING_EFFORT_SELECTED,
            parameters = mapOf(DuckChatPixelParameters.EFFORT_LEVEL to "extended_reasoning"),
        )
    }

    @Test
    fun whenUpsellTriggeredThenSingleCountWithAllParams() = runTest {
        testee.fireSubscriptionUpsellTriggered(source = "model_picker", currentTier = "free", requiredTier = "plus", flowType = "purchase")
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBSCRIPTION_UPSELL_TRIGGERED,
            parameters = mapOf(
                DuckChatPixelParameters.UPSELL_SOURCE to "model_picker",
                DuckChatPixelParameters.UPSELL_CURRENT_TIER to "free",
                DuckChatPixelParameters.UPSELL_REQUIRED_TIER to "plus",
                DuckChatPixelParameters.UPSELL_FLOW_TYPE to "purchase",
            ),
        )
    }

    @Test
    fun whenShowModelPickerThenFiresCountAndDaily() = runTest {
        testee.fireShowModelPicker()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SHOW_MODEL_PICKER_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SHOW_MODEL_PICKER_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenSubmitChangeModelThenFiresCountAndDailyWithModelId() = runTest {
        testee.fireSubmitChangeModel(modelId = "gpt-5")

        val params = mapOf(DuckChatPixelParameters.MODEL_ID to "gpt-5")
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_COUNT, parameters = params)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_DAILY,
            parameters = params,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenSubmitChangeModelPromptSentThenFiresCountAndDaily() = runTest {
        testee.fireSubmitChangeModelPromptSent()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_PROMPT_SENT_COUNT, parameters = emptyMap())
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_SUBMIT_CHANGE_MODEL_PROMPT_SENT_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
    }
}
