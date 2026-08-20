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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChatEntryPoint
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.ToggleSelection
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.helper.DuckChatTermsOfServiceHandler
import com.duckduckgo.duckchat.impl.metric.DuckAiMetricCollector
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDuckChatPixelsToolsTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val statisticsUpdater: StatisticsUpdater = mock()
    private val duckAiMetricCollector: DuckAiMetricCollector = mock()
    private val termsOfServiceHandler: DuckChatTermsOfServiceHandler = mock()
    private val duckChatInternal: DuckChatInternal = mock()
    private val regularTabRepository: TabRepository = mock()
    private val fireTabRepository: TabRepository = mock()
    private val tabRepositoryProvider: BrowserModeDataProvider<TabRepository> = mock()
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()

    @Before
    fun setup() {
        whenever(tabRepositoryProvider.forMode(BrowserMode.REGULAR)).thenReturn(regularTabRepository)
        whenever(tabRepositoryProvider.forMode(BrowserMode.FIRE)).thenReturn(fireTabRepository)
    }

    private val testee = RealDuckChatPixels(
        pixel = pixel,
        duckChatFeatureRepository = duckChatFeatureRepository,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        statisticsUpdater = statisticsUpdater,
        duckAiMetricCollector = duckAiMetricCollector,
        termsOfServiceHandler = termsOfServiceHandler,
        tabRepositoryProvider = tabRepositoryProvider,
        duckAiTabSessionRepository = mock(),
        duckDuckGoUrlDetector = duckDuckGoUrlDetector,
        duckChatInternal = duckChatInternal,
    )

    private val surfaceParams = mapOf(DuckChatPixelParameters.SURFACE to "contextual_chat")

    @Test
    fun whenImageGenerationSelectedThenFiresCountAndDaily() = runTest {
        testee.fireImageGenerationSelected(DuckChatPixelSurface.CONTEXTUAL_CHAT)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SELECTED_COUNT, parameters = surfaceParams)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SELECTED_DAILY,
            parameters = surfaceParams,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenImageGenerationDeselectedThenFiresCountAndDaily() = runTest {
        testee.fireImageGenerationDeselected(DuckChatPixelSurface.CONTEXTUAL_CHAT)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_DESELECTED_COUNT, parameters = surfaceParams)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_DESELECTED_DAILY,
            parameters = surfaceParams,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenImageGenerationSubmittedThenFiresCountAndDaily() = runTest {
        testee.fireImageGenerationSubmitted(DuckChatPixelSurface.CONTEXTUAL_CHAT)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SUBMITTED_COUNT, parameters = surfaceParams)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_IMAGE_GENERATION_SUBMITTED_DAILY,
            parameters = surfaceParams,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenWebSearchSelectedThenFiresCountAndDaily() = runTest {
        testee.fireWebSearchSelected(DuckChatPixelSurface.CONTEXTUAL_CHAT)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SELECTED_COUNT, parameters = surfaceParams)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SELECTED_DAILY,
            parameters = surfaceParams,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenWebSearchDeselectedThenFiresCountAndDaily() = runTest {
        testee.fireWebSearchDeselected(DuckChatPixelSurface.CONTEXTUAL_CHAT)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_DESELECTED_COUNT, parameters = surfaceParams)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_DESELECTED_DAILY,
            parameters = surfaceParams,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenWebSearchSubmittedThenFiresCountAndDaily() = runTest {
        testee.fireWebSearchSubmitted(DuckChatPixelSurface.CONTEXTUAL_CHAT)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SUBMITTED_COUNT, parameters = surfaceParams)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_WEB_SEARCH_SUBMITTED_DAILY,
            parameters = surfaceParams,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenCustomizeResponsesSelectedThenFiresCountAndDaily() = runTest {
        testee.fireCustomizeResponsesSelected(DuckChatPixelSurface.CONTEXTUAL_CHAT)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_CUSTOMIZE_RESPONSES_SELECTED_COUNT, parameters = surfaceParams)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_CUSTOMIZE_RESPONSES_SELECTED_DAILY,
            parameters = surfaceParams,
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
            surface = DuckChatPixelSurface.CONTEXTUAL_CHAT,
            defaultMode = null,
            tabId = null,
            addressBarEntryPoint = null,
        )

        val params = mapOf(
            DuckChatPixelParameters.SELECTED_TOOL to "image_generation",
            DuckChatPixelParameters.MODEL_ID to "gpt-5",
            DuckChatPixelParameters.REASONING_EFFORT to "fast",
            DuckChatPixelParameters.HAS_IMAGE_ATTACHMENT to "true",
            DuckChatPixelParameters.HAS_FILE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_TEXT to "true",
            DuckChatPixelParameters.SURFACE to "contextual_chat",
            DuckChatPixelParameters.PROMPT_PAGE_TYPE to "contextual",
            DuckChatPixelParameters.ENTRY_SOURCE to "contextual_chat",
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
            surface = DuckChatPixelSurface.ADDRESS_BAR,
            defaultMode = null,
            tabId = null,
            addressBarEntryPoint = null,
        )

        val params = mapOf(
            DuckChatPixelParameters.SELECTED_TOOL to "none",
            DuckChatPixelParameters.HAS_IMAGE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_FILE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_TEXT to "true",
            DuckChatPixelParameters.SURFACE to "address_bar",
            DuckChatPixelParameters.PROMPT_PAGE_TYPE to "unknown",
        )
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT, parameters = params)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_DAILY,
            parameters = params,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenPromptSubmittedFromAddressBarOnBlankTabThenPageTypeIsNtp() = runTest {
        whenever(regularTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = null))

        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.ADDRESS_BAR,
            defaultMode = null,
            tabId = "tab1",
            addressBarEntryPoint = DuckChatEntryPoint.ADDRESS_BAR_PROMPT,
        )

        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT,
            parameters = promptSubmittedAddressBarParams(pageType = "ntp"),
        )
    }

    @Test
    fun whenPromptSubmittedFromAddressBarOnWebsiteThenPageTypeIsWebsite() = runTest {
        whenever(regularTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://example.com"))
        whenever(duckChatInternal.isDuckChatUrl(any())).thenReturn(false)
        whenever(duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)

        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.ADDRESS_BAR,
            defaultMode = null,
            tabId = "tab1",
            addressBarEntryPoint = DuckChatEntryPoint.ADDRESS_BAR_PROMPT,
        )

        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT,
            parameters = promptSubmittedAddressBarParams(pageType = "website"),
        )
    }

    @Test
    fun whenPromptSubmittedFromAddressBarOnSerpThenPageTypeIsSerp() = runTest {
        whenever(regularTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = "https://duckduckgo.com/?q=test"))
        whenever(duckChatInternal.isDuckChatUrl(any())).thenReturn(false)
        whenever(duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)

        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.ADDRESS_BAR,
            defaultMode = null,
            tabId = "tab1",
            addressBarEntryPoint = DuckChatEntryPoint.ADDRESS_BAR_PROMPT,
        )

        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT,
            parameters = promptSubmittedAddressBarParams(pageType = "serp"),
        )
    }

    @Test
    fun whenPromptSubmittedFromAddressBarAndTabExistsOnlyInFireModeThenPageTypeIsResolvedFromFireRepository() = runTest {
        // The tab isn't in the Regular-mode tabs table at all — it lives in Fire mode's own database.
        whenever(regularTabRepository.getTab("fire-tab")).thenReturn(null)
        whenever(fireTabRepository.getTab("fire-tab")).thenReturn(TabEntity(tabId = "fire-tab", url = "https://example.com"))
        whenever(duckChatInternal.isDuckChatUrl(any())).thenReturn(false)
        whenever(duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)

        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.ADDRESS_BAR,
            defaultMode = null,
            tabId = "fire-tab",
            addressBarEntryPoint = DuckChatEntryPoint.ADDRESS_BAR_PROMPT,
        )

        // Must resolve to the tab's real page type, not fall back to "ntp" just because it was
        // missing from the Regular-mode repository.
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT,
            parameters = promptSubmittedAddressBarParams(pageType = "website"),
        )
    }

    @Test
    fun whenPromptSubmittedFromAddressBarAndTabIsNotFoundInEitherModeThenPageTypeIsUnknown() = runTest {
        whenever(regularTabRepository.getTab("missing-tab")).thenReturn(null)
        whenever(fireTabRepository.getTab("missing-tab")).thenReturn(null)

        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.ADDRESS_BAR,
            defaultMode = null,
            tabId = "missing-tab",
            addressBarEntryPoint = DuckChatEntryPoint.ADDRESS_BAR_PROMPT,
        )

        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT,
            parameters = promptSubmittedAddressBarParams(pageType = "unknown"),
        )
    }

    @Test
    fun whenPromptSubmittedFromAddressBarWithVoiceEntryPointThenSourceIsVoice() = runTest {
        whenever(regularTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = null))

        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.ADDRESS_BAR,
            defaultMode = null,
            tabId = "tab1",
            addressBarEntryPoint = DuckChatEntryPoint.VOICE,
        )

        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT,
            parameters = promptSubmittedAddressBarParams(pageType = "ntp", source = "voice"),
        )
    }

    @Test
    fun whenPromptSubmittedFromAddressBarWithNoEntryPointThenSourceIsOmitted() = runTest {
        whenever(regularTabRepository.getTab("tab1")).thenReturn(TabEntity(tabId = "tab1", url = null))

        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.ADDRESS_BAR,
            defaultMode = null,
            tabId = "tab1",
            addressBarEntryPoint = null,
        )

        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT,
            parameters = promptSubmittedAddressBarParams(pageType = "ntp", source = null),
        )
    }

    private fun promptSubmittedAddressBarParams(pageType: String, source: String? = "address_bar_prompt") = buildMap {
        put(DuckChatPixelParameters.SELECTED_TOOL, "none")
        put(DuckChatPixelParameters.HAS_IMAGE_ATTACHMENT, "false")
        put(DuckChatPixelParameters.HAS_FILE_ATTACHMENT, "false")
        put(DuckChatPixelParameters.HAS_TEXT, "true")
        put(DuckChatPixelParameters.SURFACE, "address_bar")
        put(DuckChatPixelParameters.PROMPT_PAGE_TYPE, pageType)
        source?.let { put(DuckChatPixelParameters.ENTRY_SOURCE, it) }
    }

    @Test
    fun whenPromptSubmittedFromAddressBarThenDefaultModeIsIncluded() = runTest {
        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.ADDRESS_BAR,
            defaultMode = ToggleSelection.DUCK_AI,
            tabId = null,
            addressBarEntryPoint = DuckChatEntryPoint.ADDRESS_BAR_PROMPT,
        )

        val params = mapOf(
            DuckChatPixelParameters.SELECTED_TOOL to "none",
            DuckChatPixelParameters.HAS_IMAGE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_FILE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_TEXT to "true",
            DuckChatPixelParameters.SURFACE to "address_bar",
            DuckChatPixelParameters.DEFAULT_MODE to "duck_ai",
            DuckChatPixelParameters.PROMPT_PAGE_TYPE to "unknown",
            DuckChatPixelParameters.ENTRY_SOURCE to "address_bar_prompt",
        )
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT, parameters = params)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_DAILY,
            parameters = params,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenPromptSubmittedFromNonAddressBarSurfaceThenDefaultModeIsOmittedEvenIfProvided() = runTest {
        // The Search/Duck.ai toggle only ever shows in the address-bar surface, so default_mode
        // must not leak in from surfaces where no toggle was on screen.
        testee.firePromptSubmitted(
            selectedTool = "none",
            modelId = null,
            reasoningEffort = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.CONTEXTUAL_CHAT,
            defaultMode = ToggleSelection.DUCK_AI,
            tabId = null,
            addressBarEntryPoint = null,
        )

        val params = mapOf(
            DuckChatPixelParameters.SELECTED_TOOL to "none",
            DuckChatPixelParameters.HAS_IMAGE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_FILE_ATTACHMENT to "false",
            DuckChatPixelParameters.HAS_TEXT to "true",
            DuckChatPixelParameters.SURFACE to "contextual_chat",
            DuckChatPixelParameters.PROMPT_PAGE_TYPE to "contextual",
            DuckChatPixelParameters.ENTRY_SOURCE to "contextual_chat",
        )
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_COUNT, parameters = params)
        verify(pixel).fire(
            DuckChatPixelName.DUCK_CHAT_UNIFIED_INPUT_PROMPT_SUBMITTED_DAILY,
            parameters = params,
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenDuckAiChatHistorySuggestionClickedThenFiresSingleCountPixel() = runTest {
        testee.fireDuckAiChatHistorySuggestionClicked()

        verify(pixel).fire(DuckChatPixelName.AUTOCOMPLETE_DUCKAI_CLICK_CHAT_HISTORY)
    }

    @Test
    fun whenDuckAiSearchDuckDuckGoSuggestionClickedThenFiresSingleCountPixel() = runTest {
        testee.fireDuckAiSearchDuckDuckGoSuggestionClicked()

        verify(pixel).fire(DuckChatPixelName.AUTOCOMPLETE_DUCKAI_CLICK_SEARCH_DUCKDUCKGO)
    }
}
