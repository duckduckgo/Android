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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageWindow
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class RealUsageWarningPixelSenderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val testee = RealUsageWarningPixelSender(pixel, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

    private val approaching = UsageWarningExposure(UsageWarningExposureKind.APPROACHING, UsageWindow.WEEKLY, percentBucket = 75)
    private val limitReached = UsageWarningExposure(UsageWarningExposureKind.LIMIT_REACHED, UsageWindow.DAILY)
    private val notice = UsageWarningExposure(UsageWarningExposureKind.HIGH_USAGE_MODEL_NOTICE, modelId = "claude-opus-4-8")

    @Test
    fun whenAnApproachingCardIsShownThenCountAndDailyFireWithRungWindowAndSurface() = runTest {
        testee.send(UsageWarningEvent.Shown(approaching), DuckChatPixelSurface.DUCK_AI)

        val params = mapOf("surface" to "duck_ai", "window" to "weekly", "percent_bucket" to "75")
        verifyCount("m_aichat_usage_warning_approaching_shown_count", params)
        verifyDaily("m_aichat_usage_warning_approaching_shown_daily", params)
    }

    @Test
    fun whenAReachedLimitIsShownThenItReportsTheWindowWithoutARung() = runTest {
        testee.send(UsageWarningEvent.Shown(limitReached), DuckChatPixelSurface.ADDRESS_BAR)

        verifyCount("m_aichat_usage_warning_limit_reached_shown_count", mapOf("surface" to "address_bar", "window" to "daily"))
    }

    @Test
    fun whenAReachedLimitReportsADismissalThenNothingIsFired() = runTest {
        testee.send(UsageWarningEvent.Dismissed(limitReached), DuckChatPixelSurface.DUCK_AI)

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenTheHighUsageNoticeLeadsToAModelSwitchThenItReportsTheModelSwitchedAwayFrom() = runTest {
        testee.send(UsageWarningEvent.ModelSwitched(notice), DuckChatPixelSurface.CONTEXTUAL_CHAT)

        verifyCount(
            "m_aichat_high_usage_model_notice_model_switched_count",
            mapOf("surface" to "contextual_chat", "model_id" to "claude-opus-4-8"),
        )
    }

    @Test
    fun whenEachCtaIsTappedThenItReportsItsOwnSeries() = runTest {
        testee.send(UsageWarningEvent.CtaTapped(approaching, UsageWarningCta.SWITCH_MODEL), DuckChatPixelSurface.DUCK_AI)
        testee.send(UsageWarningEvent.CtaTapped(limitReached, UsageWarningCta.UPSELL), DuckChatPixelSurface.DUCK_AI)
        testee.send(UsageWarningEvent.CtaTapped(limitReached, UsageWarningCta.WEEKLY_LIMIT), DuckChatPixelSurface.DUCK_AI)

        verifyCount(
            "m_aichat_usage_warning_switch_model_tapped_count",
            mapOf("surface" to "duck_ai", "window" to "weekly", "percent_bucket" to "75"),
        )
        verifyCount("m_aichat_usage_warning_upsell_tapped_count", mapOf("surface" to "duck_ai", "window" to "daily"))
        verifyCount("m_aichat_usage_warning_weekly_limit_tapped_count", mapOf("surface" to "duck_ai", "window" to "daily"))
    }

    @Test
    fun whenEveryEventKindIsMappedThenOnlyTheReachedDismissalIsLeftOut() {
        val events = listOf(approaching, limitReached, notice).flatMap { exposure ->
            listOf(
                UsageWarningEvent.Shown(exposure),
                UsageWarningEvent.Dismissed(exposure),
                UsageWarningEvent.PromptSubmitted(exposure),
                UsageWarningEvent.ModelSwitched(exposure),
                UsageWarningEvent.Abandoned(exposure),
            )
        }

        val unmapped = events.filter { it.pixelName() == null }

        assertEquals(listOf<UsageWarningEvent>(UsageWarningEvent.Dismissed(limitReached)), unmapped)
        assertTrue(UsageWarningPixelName.entries.all { it.count.pixelName.startsWith("m_aichat_") && it.count.pixelName.endsWith("_count") })
        assertTrue(UsageWarningPixelName.entries.all { it.daily.pixelName.endsWith("_daily") })
    }

    private fun verifyCount(
        name: String,
        params: Map<String, String>,
    ) = verify(pixel).fire(argThat<Pixel.PixelName> { pixelName == name }, eq(params), eq(emptyMap()), eq(Pixel.PixelType.Count))

    private fun verifyDaily(
        name: String,
        params: Map<String, String>,
    ) = verify(pixel).fire(argThat<Pixel.PixelName> { pixelName == name }, eq(params), eq(emptyMap()), eq(Pixel.PixelType.Daily()))
}
