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

import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNotice
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNoticeId
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageWindow
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningEvent.Abandoned
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningEvent.CtaTapped
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningEvent.Dismissed
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningEvent.ModelSwitched
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningEvent.PromptSubmitted
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningEvent.Shown
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageWarningMeasurementTest {

    private val sender = FakeUsageWarningPixelSender()
    private var surface = DuckChatPixelSurface.DUCK_AI
    private val testee = UsageWarningMeasurement(sender) { surface }

    private val approaching = UsageWarningExposure(UsageWarningExposureKind.APPROACHING, UsageWindow.WEEKLY, percentBucket = 75)
    private val limitReached = UsageWarningExposure(UsageWarningExposureKind.LIMIT_REACHED, UsageWindow.WEEKLY)
    private val notice = UsageWarningExposure(UsageWarningExposureKind.HIGH_USAGE_MODEL_NOTICE, modelId = "claude-opus-4-8")

    @Test
    fun whenTheCardBecomesVisibleThenItIsReportedAsShown() {
        testee.cardBecameVisible(approaching)

        assertEquals(listOf(Shown(approaching)), sender.events)
    }

    @Test
    fun whenTheSameCardBecomesVisibleAgainThenItIsNotReportedTwice() {
        testee.cardBecameVisible(approaching)
        testee.cardBecameVisible(approaching)

        assertEquals(listOf(Shown(approaching)), sender.events)
    }

    @Test
    fun whenADifferentRungBecomesVisibleThenItIsReportedAsANewImpression() {
        val ninety = approaching.copy(percentBucket = 90)

        testee.cardBecameVisible(approaching)
        testee.cardBecameVisible(ninety)

        assertEquals(listOf(Shown(approaching), Abandoned(approaching), Shown(ninety)), sender.events)
    }

    @Test
    fun whenTheInputSessionEndsWithNoFollowUpThenTheExposureIsReportedAsAbandoned() {
        testee.cardBecameVisible(limitReached)
        testee.inputSessionEnded()

        assertEquals(listOf(Shown(limitReached), Abandoned(limitReached)), sender.events)
    }

    @Test
    fun whenTheInputSessionEndsTwiceThenAbandonedIsReportedOnce() {
        testee.cardBecameVisible(limitReached)
        testee.inputSessionEnded()
        testee.inputSessionEnded()

        assertEquals(listOf(Shown(limitReached), Abandoned(limitReached)), sender.events)
    }

    @Test
    fun whenAPromptIsSubmittedThenTheExposureIsNotReportedAsAbandoned() {
        testee.cardBecameVisible(approaching)
        testee.promptSubmitted()
        testee.inputSessionEnded()

        assertEquals(listOf(Shown(approaching), PromptSubmitted(approaching)), sender.events)
    }

    @Test
    fun whenTwoPromptsAreSubmittedInOneSessionThenOnlyTheFirstIsReported() {
        testee.cardBecameVisible(approaching)
        testee.promptSubmitted()
        testee.promptSubmitted()

        assertEquals(listOf(Shown(approaching), PromptSubmitted(approaching)), sender.events)
    }

    @Test
    fun whenTheUserSwitchesModelThenTheExposureIsReportedAsAModelSwitch() {
        testee.cardBecameVisible(notice)
        testee.modelSwitched()
        testee.inputSessionEnded()

        assertEquals(listOf(Shown(notice), ModelSwitched(notice)), sender.events)
    }

    @Test
    fun whenTheUserBothSwitchesModelAndPromptsThenBothAreReported() {
        testee.cardBecameVisible(approaching)
        testee.modelSwitched()
        testee.promptSubmitted()
        testee.inputSessionEnded()

        assertEquals(listOf(Shown(approaching), ModelSwitched(approaching), PromptSubmitted(approaching)), sender.events)
    }

    @Test
    fun whenNothingHasBeenShownThenAFollowUpReportsNothing() {
        testee.promptSubmitted()
        testee.modelSwitched()
        testee.warningDismissed()
        testee.ctaTapped(UsageWarningCta.UPSELL)
        testee.inputSessionEnded()

        assertTrue(sender.events.isEmpty())
    }

    @Test
    fun whenTheCardIsDismissedAndAPromptFollowsThenBothAreReported() {
        testee.cardBecameVisible(approaching)
        testee.warningDismissed()
        testee.promptSubmitted()
        testee.inputSessionEnded()

        assertEquals(listOf(Shown(approaching), Dismissed(approaching), PromptSubmitted(approaching)), sender.events)
    }

    @Test
    fun whenTheCardIsDismissedAndNothingFollowsThenTheExposureIsReportedAsAbandoned() {
        testee.cardBecameVisible(approaching)
        testee.warningDismissed()
        testee.inputSessionEnded()

        assertEquals(listOf(Shown(approaching), Dismissed(approaching), Abandoned(approaching)), sender.events)
    }

    @Test
    fun whenTheSwitchFollowsTheCardsOwnCtaThenItIsNotAlsoReportedAsAModelSwitch() {
        testee.cardBecameVisible(approaching)
        testee.ctaTapped(UsageWarningCta.SWITCH_MODEL)
        testee.modelSwitched()
        testee.inputSessionEnded()

        assertEquals(listOf(Shown(approaching), CtaTapped(approaching, UsageWarningCta.SWITCH_MODEL)), sender.events)
    }

    @Test
    fun whenACtaIsTappedThenTheExposureIsNotReportedAsAbandoned() {
        testee.cardBecameVisible(limitReached)
        testee.ctaTapped(UsageWarningCta.WEEKLY_LIMIT)
        testee.inputSessionEnded()

        assertEquals(listOf(Shown(limitReached), CtaTapped(limitReached, UsageWarningCta.WEEKLY_LIMIT)), sender.events)
    }

    @Test
    fun whenTheSurfaceChangesThenTheNextEventReportsTheNewSurface() {
        testee.cardBecameVisible(approaching)
        surface = DuckChatPixelSurface.CONTEXTUAL_CHAT
        testee.promptSubmitted()

        assertEquals(listOf(DuckChatPixelSurface.DUCK_AI, DuckChatPixelSurface.CONTEXTUAL_CHAT), sender.surfaces)
    }

    @Test
    fun whenAnExposureIsBuiltFromANoticeThenItCarriesTheRungAndWindow() {
        assertEquals(75, UsageWarningExposure.of(notice(percent = 75)).percentBucket)
        assertEquals(90, UsageWarningExposure.of(notice(percent = 99)).percentBucket)
        assertEquals(50, UsageWarningExposure.of(notice(percent = 50)).percentBucket)
        assertEquals(UsageWindow.DAILY, UsageWarningExposure.of(notice(percent = 50)).window)
        assertEquals(UsageWarningExposureKind.APPROACHING, UsageWarningExposure.of(notice(percent = 50)).kind)
    }

    @Test
    fun whenAnExposureIsBuiltFromAReachedNoticeThenItHasNoRung() {
        val exposure = UsageWarningExposure.of(notice(percent = 100, reached = true))

        assertEquals(UsageWarningExposureKind.LIMIT_REACHED, exposure.kind)
        assertEquals(UsageWindow.DAILY, exposure.window)
        assertNull(exposure.percentBucket)
    }

    private fun notice(
        percent: Int,
        reached: Boolean = false,
    ) = UsageNotice(
        id = if (reached) UsageNoticeId.DAILY_REACHED else UsageNoticeId.APPROACHING,
        window = UsageWindow.DAILY,
        percentUsed = percent,
        resetsAtMillis = 1L,
        reached = reached,
        dismissible = !reached,
    )
}
