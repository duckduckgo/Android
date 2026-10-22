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
package com.duckduckgo.nextsteps.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.Days
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDate

class RealNextStepsTelemetryTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val sharedPreferences = InMemorySharedPreferences()
    private val preferences: SharedPreferencesProvider = mock {
        whenever(it.getSharedPreferences(any(), any(), any())).thenReturn(sharedPreferences)
    }
    private val telemetryStore = RealNextStepsTelemetryStore(preferences)
    private val pixels: NextStepsPixels = mock()
    private val clock: CurrentTimeProvider = mock()
    private val firstDay = LocalDate.of(2026, 10, 21)
    private var today = firstDay
    private val testee = RealNextStepsTelemetry(telemetryStore, pixels, clock, coroutineRule.testDispatcherProvider)

    init {
        whenever(clock.localDateTimeNow()).thenAnswer { today.atStartOfDay() }
    }

    @Test
    fun `section shown unique is sent only on the first render of the install`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onActiveMessageChanged(null)
        testee.onFrontCardShown("message2", B, setOf(B))

        verify(pixels, times(1)).sectionShownUnique()
    }

    @Test
    fun `first impression marks the message displayed and seeds every card in the stack with zero`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))

        verify(pixels).sectionShownUnique()
        verify(pixels).cardImpression(A, DAY_0)
        val state = telemetryStore.state()
        assertEquals("message", state.displayedMessageId)
        assertEquals(firstDay.toEpochDay(), state.firstRenderDay)
        assertEquals(1, state.activeDays)
        assertEquals(mapOf(A to 1, B to 0), state.dayCounts)
    }

    @Test
    fun `completion sends the card's day count immediately and never again`() = runTest {
        repeat(2) { testee.onFrontCardShown("message", A, setOf(A, B)) }
        testee.onCtaClicked(A)

        testee.onCardsCompleted(setOf(A))

        verify(pixels).cardCompleted(A, DAY_0)
        verify(pixels).cardDailyImpressions(A, DAY_0, "2")
        assertEquals(mapOf(B to 0), telemetryStore.state().dayCounts)

        today = today.plusDays(1)
        testee.onFrontCardShown("message", B, setOf(B))
        verify(pixels, times(1)).cardDailyImpressions(A, DAY_0, "2")
        verify(pixels).cardDailyImpressions(B, DAY_0, "0")
    }

    @Test
    fun `completion without a CTA tap sends the day count but no completion pixel`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))

        testee.onCardsCompleted(setOf(A))

        verify(pixels).cardDailyImpressions(A, DAY_0, "1")
        verify(pixels, never()).cardCompleted(any(), any())
    }

    @Test
    fun `only the last tapped CTA receives the completion pixel`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onCtaClicked(A)
        testee.onCtaClicked(B)

        testee.onCardsCompleted(setOf(A, B))

        verify(pixels, never()).cardCompleted(A, DAY_0)
        verify(pixels).cardCompleted(B, DAY_0)
        assertNull(telemetryStore.state().ctaClickedCardId)
    }

    @Test
    fun `a second completion report of the same card sends nothing`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        testee.onCtaClicked(A)

        val reports = (1..2).map { async { testee.onCardsCompleted(setOf(A)) } }
        reports.awaitAll()

        verify(pixels, times(1)).cardCompleted(A, DAY_0)
        verify(pixels, times(1)).cardDailyImpressions(A, DAY_0, "1")
        assertEquals(1, telemetryStore.state().completedCards)
    }

    @Test
    fun `dismissing a card sends its day count and clears its CTA attribution`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onCtaClicked(A)

        testee.onCardDismissed(A)

        verify(pixels).cardDismissed(A, DAY_0)
        verify(pixels).cardDailyImpressions(A, DAY_0, "1")
        assertNull(telemetryStore.state().ctaClickedCardId)
        assertEquals(mapOf(B to 0), telemetryStore.state().dayCounts)
    }

    @Test
    fun `all cards done sends the remaining day counts and the summary then resets`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B, C))
        testee.onCtaClicked(A)
        testee.onCardsCompleted(setOf(A))
        testee.onCardDismissed(B)
        testee.onCardsCompleted(setOf(C))

        testee.onAllCardsDone()
        testee.onActiveMessageChanged(null)

        verify(pixels).cardsDismissed(DAY_0, "all_cards_done", completed = 2, dismissed = 1)
        verify(pixels, times(1)).cardsDismissed(any(), any(), any(), any())
        val state = telemetryStore.state()
        assertNull(state.displayedMessageId)
        assertEquals(emptyMap<String, Int>(), state.dayCounts)
        assertEquals(0, state.completedCards)
        assertEquals(0, state.dismissedCards)
        assertEquals(firstDay.toEpochDay(), state.firstRenderDay)
    }

    @Test
    fun `cards completed before the first impression are not counted in the summary`() = runTest {
        testee.onCardsCompleted(setOf(A, B))
        verifyNoInteractions(pixels)

        testee.onFrontCardShown("message", C, setOf(C))
        testee.onCardDismissed(C)
        testee.onAllCardsDone()

        verify(pixels).cardsDismissed(DAY_0, "all_cards_done", completed = 0, dismissed = 1)
    }

    @Test
    fun `the first impression of a later day sends every previous day count including zeros`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B, C))
        today = today.plusDays(1)

        testee.onFrontCardShown("message", B, setOf(A, B, C))

        verify(pixels).cardDailyImpressions(A, DAY_0, "1")
        verify(pixels).cardDailyImpressions(B, DAY_0, "0")
        verify(pixels).cardDailyImpressions(C, DAY_0, "0")
        verify(pixels).cardImpression(B, Days(activeDay = "1", calendarDay = "1"))
        assertEquals(mapOf(A to 0, B to 1, C to 0), telemetryStore.state().dayCounts)
    }

    @Test
    fun `active day counts only days with an impression while calendar day counts every day`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        today = today.plusDays(3)

        testee.onFrontCardShown("message", A, setOf(A))

        verify(pixels).cardImpression(A, Days(activeDay = "1", calendarDay = "3"))
        assertEquals(2, telemetryStore.state().activeDays)
    }

    @Test
    fun `completion on a later day keeps the day count on the day it was counted`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        testee.onCtaClicked(A)
        today = today.plusDays(1)

        testee.onCardsCompleted(setOf(A))

        verify(pixels).cardDailyImpressions(A, DAY_0, "1")
        verify(pixels).cardCompleted(A, Days(activeDay = "0", calendarDay = "1"))
    }

    @Test
    fun `the message leaving the slot ends the carousel once with reason auto`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))

        testee.onActiveMessageChanged(null)
        testee.onActiveMessageChanged(null)

        verify(pixels, times(1)).cardsDismissed(DAY_0, "auto", completed = 0, dismissed = 0)
        verify(pixels, times(1)).cardDailyImpressions(A, DAY_0, "1")
        verify(pixels, times(1)).cardDailyImpressions(B, DAY_0, "0")
        assertNull(telemetryStore.state().displayedMessageId)
    }

    @Test
    fun `another message taking the slot ends the carousel with reason auto`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))

        testee.onActiveMessageChanged("other_message")

        verify(pixels).cardsDismissed(DAY_0, "auto", completed = 0, dismissed = 0)
    }

    @Test
    fun `the displayed message staying in the slot sends nothing`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        clearInvocations(pixels)

        testee.onActiveMessageChanged("message")

        verifyNoInteractions(pixels)
    }

    @Test
    fun `the slot changing with no carousel displayed sends nothing`() = runTest {
        testee.onActiveMessageChanged(null)
        testee.onActiveMessageChanged("message")

        verifyNoInteractions(pixels)
    }

    @Test
    fun `after the user ends the carousel the message leaving the slot sends nothing more`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        testee.onCardDismissed(A)
        testee.onAllCardsDone()
        clearInvocations(pixels)

        testee.onActiveMessageChanged(null)

        verifyNoInteractions(pixels)
    }

    @Test
    fun `an impression for a new message ends the old carousel and keeps the day anchor`() = runTest {
        testee.onFrontCardShown("old", A, setOf(A, B))
        today = today.plusDays(4)

        testee.onFrontCardShown("new", B, setOf(A, B))

        verify(pixels).cardsDismissed(Days(activeDay = "0", calendarDay = "4-7"), "auto", completed = 0, dismissed = 0)
        verify(pixels).cardDailyImpressions(A, DAY_0, "1")
        verify(pixels).cardDailyImpressions(B, DAY_0, "0")
        verify(pixels).cardImpression(B, Days(activeDay = "1", calendarDay = "4-7"))
        assertEquals("new", telemetryStore.state().displayedMessageId)
        assertEquals(firstDay.toEpochDay(), telemetryStore.state().firstRenderDay)
    }

    @Test
    fun `state survives a restart`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onCtaClicked(A)
        val restored = RealNextStepsTelemetry(RealNextStepsTelemetryStore(preferences), pixels, clock, coroutineRule.testDispatcherProvider)

        restored.onCardsCompleted(setOf(A))
        restored.onActiveMessageChanged(null)

        verify(pixels).cardCompleted(A, DAY_0)
        verify(pixels).cardDailyImpressions(A, DAY_0, "1")
        verify(pixels).cardDailyImpressions(B, DAY_0, "0")
        verify(pixels).cardsDismissed(DAY_0, "auto", completed = 1, dismissed = 0)
    }

    @Test
    fun `a clock moved backwards keeps counting into the open day`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        today = today.plusDays(2)
        testee.onFrontCardShown("message", A, setOf(A))
        clearInvocations(pixels)
        today = today.minusDays(1)

        testee.onFrontCardShown("message", A, setOf(A))

        verify(pixels).cardImpression(A, Days(activeDay = "1", calendarDay = "1"))
        verify(pixels, never()).cardDailyImpressions(any(), any(), any())
        assertEquals(firstDay.plusDays(2).toEpochDay(), telemetryStore.state().openDay)
        assertEquals(mapOf(A to 2), telemetryStore.state().dayCounts)
    }

    @Test
    fun `calendar day buckets cover the boundaries and clamp dates before the first render`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        for ((offset, expected) in listOf(
            -1L to "0", 0L to "0", 1L to "1", 2L to "2", 3L to "3", 4L to "4-7",
            7L to "4-7", 8L to "8-14", 14L to "8-14", 15L to "15+",
        )) {
            clearInvocations(pixels)
            today = firstDay.plusDays(offset)
            testee.onCtaClicked(A)
            verify(pixels).cardCtaClicked(A, Days(activeDay = "0", calendarDay = expected))
        }
    }

    @Test
    fun `active day buckets cover the boundaries`() = runTest {
        val boundaries = listOf(0 to "0", 1 to "0", 2 to "1", 4 to "3", 5 to "4-7", 8 to "4-7", 9 to "8-14", 15 to "8-14", 16 to "15+")
        for ((activeDays, expected) in boundaries) {
            telemetryStore.save(NextStepsTelemetryState(firstRenderDay = firstDay.toEpochDay(), activeDays = activeDays))
            clearInvocations(pixels)

            testee.onCtaClicked(A)

            verify(pixels).cardCtaClicked(A, Days(activeDay = expected, calendarDay = "0"))
        }
    }

    @Test
    fun `impression buckets cover the boundaries`() = runTest {
        for ((count, expected) in listOf(0 to "0", 1 to "1", 2 to "2", 3 to "3-4", 4 to "3-4", 5 to "5-7", 7 to "5-7", 8 to "8+", 20 to "8+")) {
            telemetryStore.save(
                NextStepsTelemetryState(
                    firstRenderDay = firstDay.toEpochDay(),
                    activeDays = 1,
                    displayedMessageId = "message",
                    openDay = firstDay.toEpochDay(),
                    dayCounts = mapOf(A to count),
                ),
            )
            clearInvocations(pixels)

            testee.onActiveMessageChanged(null)

            verify(pixels).cardDailyImpressions(A, DAY_0, expected)
        }
    }

    @Test
    fun `an event before any impression reports day zero`() = runTest {
        testee.onCtaClicked(A)

        verify(pixels).cardCtaClicked(A, DAY_0)
    }

    @Test
    fun `days without an impression produce no day counts`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        today = today.plusDays(3)

        testee.onActiveMessageChanged(null)

        verify(pixels, times(1)).cardDailyImpressions(any(), any(), any())
        verify(pixels).cardDailyImpressions(A, DAY_0, "1")
    }

    private companion object {
        const val A = "setup_default_browser"
        const val B = "setup_add_widget"
        const val C = "setup_sync"
        val DAY_0 = Days(activeDay = "0", calendarDay = "0")
    }
}
