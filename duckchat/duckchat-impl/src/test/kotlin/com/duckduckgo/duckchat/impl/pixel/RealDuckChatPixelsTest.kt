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
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_CREATE_NEW_CHAT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_OPEN_HISTORY
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SELECT_FIRST_HISTORY_ITEM
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_FIRST_PROMPT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_SUBMIT_PROMPT
import com.duckduckgo.duckchat.impl.ReportMetric.USER_DID_TAP_KEYBOARD_RETURN_KEY
import com.duckduckgo.duckchat.impl.metric.DuckAiMetricCollector
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_COLLECTION_EMPTY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_COUNT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_KEYBOARD_RETURN_PRESSED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN_HISTORY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_OPEN_MOST_RECENT_HISTORY_CHAT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SEND_PROMPT_ONGOING_CHAT
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_START_NEW_CONVERSATION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_START_NEW_CONVERSATION_BUTTON_CLICKED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN_DAILY
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealDuckChatPixelsTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    private val mockDuckChatFeatureRepository: DuckChatFeatureRepository = mock()

    private val statisticsUpdater: StatisticsUpdater = mock()
    private val duckAiMetricCollector: DuckAiMetricCollector = mock()

    private lateinit var testee: RealDuckChatPixels

    @Before
    fun setup() = runTest {
        whenever(mockDuckChatFeatureRepository.sessionDeltaInMinutes()).thenReturn(1)

        testee = RealDuckChatPixels(
            pixel = mockPixel,
            duckChatFeatureRepository = mockDuckChatFeatureRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            statisticsUpdater = statisticsUpdater,
            duckAiMetricCollector = duckAiMetricCollector,
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
        verify(statisticsUpdater).refreshDuckAiRetentionAtb()
        verify(duckAiMetricCollector).onMessageSent()
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
        verify(statisticsUpdater).refreshDuckAiRetentionAtb()
        verify(duckAiMetricCollector).onMessageSent()
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
        verifyNoInteractions(statisticsUpdater)
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
        verifyNoInteractions(statisticsUpdater)
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
        verifyNoInteractions(statisticsUpdater)
    }

    @Test
    fun `when sendReportMetricPixel with USER_DID_TAP_KEYBOARD_RETURN_KEY then fires correct pixel with empty params`() = runTest {
        testee.sendReportMetricPixel(USER_DID_TAP_KEYBOARD_RETURN_KEY)

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_KEYBOARD_RETURN_PRESSED, parameters = emptyMap())
        verifyNoInteractions(statisticsUpdater)
    }

    @Test
    fun `when reportOpen called then all pixels are sent`() = runTest {
        val sessionDelta = 10L
        whenever(mockDuckChatFeatureRepository.sessionDeltaInMinutes()).thenReturn(sessionDelta)

        testee.reportOpen()

        coroutineRule.testScope.advanceUntilIdle()

        verify(mockDuckChatFeatureRepository).registerOpened()

        val params = mapOf(DuckChatPixelParameters.DELTA_TIMESTAMP_PARAMETERS to sessionDelta.toString())
        verify(mockPixel).fire(DUCK_CHAT_OPEN, parameters = params)
        verify(mockPixel).fire(PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN)
        verify(mockPixel).fire(PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPageContextManuallyAttachedNative then fires count and daily`() = runTest {
        testee.reportContextualPageContextManuallyAttachedNative()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_NATIVE_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPageContextManuallyAttachedFrontend then fires count and daily`() = runTest {
        testee.reportContextualPageContextManuallyAttachedFrontend()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_MANUALLY_ATTACHED_FRONTEND_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPageContextRemovedNative then fires count and daily`() = runTest {
        testee.reportContextualPageContextRemovedNative()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_NATIVE_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPageContextRemovedFrontend then fires count and daily`() = runTest {
        testee.reportContextualPageContextRemovedFrontend()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_REMOVED_FRONTEND_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPageContextAutoAttached then fires count and daily`() = runTest {
        testee.reportContextualPageContextAutoAttached()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_AUTO_ATTACHED_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPromptSubmittedWithContextNative then fires count and daily`() = runTest {
        testee.reportContextualPromptSubmittedWithContextNative()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITH_CONTEXT_NATIVE_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPromptSubmittedWithoutContextNative then fires count and daily`() = runTest {
        testee.reportContextualPromptSubmittedWithoutContextNative()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PROMPT_SUBMITTED_WITHOUT_CONTEXT_NATIVE_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPageContextCollectionEmpty then fires count and daily`() = runTest {
        testee.reportContextualPageContextCollectionEmpty()
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_COLLECTION_EMPTY)
    }

    @Test
    fun `when reportContextualSettingAutomaticPageContentToggled enabled then fires count and daily`() = runTest {
        testee.reportContextualSettingAutomaticPageContentToggled(true)

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_ENABLED_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualSettingAutomaticPageContentToggled disabled then fires count and daily`() = runTest {
        testee.reportContextualSettingAutomaticPageContentToggled(false)

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_SETTING_AUTOMATIC_PAGE_CONTENT_DISABLED_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualSummarizePromptSelected then fires count and daily`() = runTest {
        testee.reportContextualSummarizePromptSelected()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_QUICK_ACTION_SUMMARISE_SELECTED_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPlaceholderContextTapped then fires count and daily`() = runTest {
        testee.reportContextualPlaceholderContextTapped()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_TAPPED_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reportContextualPlaceholderContextShown then fires count and daily`() = runTest {
        testee.reportContextualPlaceholderContextShown()

        advanceUntilIdle()

        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_COUNT)
        verify(mockPixel).fire(DUCK_CHAT_CONTEXTUAL_PAGE_CONTEXT_PLACEHOLDER_SHOWN_DAILY, type = Pixel.PixelType.Daily())
    }
}
