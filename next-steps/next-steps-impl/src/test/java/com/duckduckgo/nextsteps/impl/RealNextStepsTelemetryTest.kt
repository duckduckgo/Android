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
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.RemovalReason.ALREADY_COMPLETE
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.RemovalReason.COMPLETED
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.RemovalReason.DISMISSED
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.RemovalReason.FINISHED_ELSEWHERE
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.Variant.CHECK_LIST
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.Variant.STACKED_CARDS
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
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
    fun `section shown unique is requested once for each displayed message`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onActiveMessageChanged(null)
        testee.onFrontCardShown("message2", B, setOf(B))
        val restored = RealNextStepsTelemetry(RealNextStepsTelemetryStore(preferences), pixels, clock, coroutineRule.testDispatcherProvider)
        restored.onFrontCardShown("message2", B, setOf(B))

        verify(pixels, times(1)).sectionShownUnique("message", STACKED_CARDS)
        verify(pixels, times(1)).sectionShownUnique("message2", STACKED_CARDS)
    }

    @Test
    fun `first impression marks the message displayed and seeds every card in the stack with zero`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))

        verify(pixels).sectionShownUnique("message", STACKED_CARDS)
        val state = telemetryStore.state()
        assertEquals("message", state.displayedMessageId)
        assertEquals(firstDay.toEpochDay(), state.firstRenderDay)
        assertEquals(1, state.activeDays)
        assertEquals(mapOf(A to 1, B to 0), state.dayCounts)
        assertEquals(mapOf(A to 1), state.totalImpressions)
    }

    @Test
    fun `completion sends the card's day count immediately and never again`() = runTest {
        repeat(2) { testee.onFrontCardShown("message", A, setOf(A, B)) }
        testee.onCtaClicked(STACKED_CARDS, A)

        testee.onItemsCompleted(STACKED_CARDS, setOf(A))

        verify(pixels).itemRemoved(STACKED_CARDS, A, DAY_0, COMPLETED, "2")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "2")
        assertEquals(mapOf(B to 0), telemetryStore.state().dayCounts)

        today = today.plusDays(1)
        testee.onFrontCardShown("message", B, setOf(B))
        verify(pixels, times(1)).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "2")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
    }

    @Test
    fun `completion without a CTA tap is reported as finished elsewhere`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))

        testee.onItemsCompleted(STACKED_CARDS, setOf(A))

        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels).itemRemoved(STACKED_CARDS, A, DAY_0, FINISHED_ELSEWHERE, "1")
    }

    @Test
    fun `a card in the stack that was never in front is reported as finished elsewhere`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))

        testee.onItemsCompleted(STACKED_CARDS, setOf(B))

        verify(pixels).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
        verify(pixels).itemRemoved(STACKED_CARDS, B, DAY_0, FINISHED_ELSEWHERE, "0")
    }

    @Test
    fun `a card already complete at first evaluation is reported once across tabs`() = runTest {
        testee.onItemsCompleted(STACKED_CARDS, setOf(A))
        testee.onItemsCompleted(STACKED_CARDS, setOf(A))

        verify(pixels, times(1)).itemRemoved(STACKED_CARDS, A, DAY_0, ALREADY_COMPLETE, "0")
    }

    @Test
    fun `completion of a dismissed card sends nothing`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onItemDismissed(A)
        clearInvocations(pixels)

        testee.onItemsCompleted(STACKED_CARDS, setOf(A))

        verifyNoInteractions(pixels)
    }

    @Test
    fun `completion before another section renders is reported as already complete once`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onActiveMessageChanged(null)
        clearInvocations(pixels)

        testee.onItemsCompleted(STACKED_CARDS, setOf(B))
        testee.onItemsCompleted(STACKED_CARDS, setOf(B))

        verify(pixels, times(1)).itemRemoved(STACKED_CARDS, B, DAY_0, ALREADY_COMPLETE, "0")
        verifyNoMoreInteractions(pixels)
    }

    @Test
    fun `only the last tapped CTA is reported as completed`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onCtaClicked(STACKED_CARDS, A)
        testee.onCtaClicked(STACKED_CARDS, B)

        testee.onItemsCompleted(STACKED_CARDS, setOf(A, B))

        verify(pixels).itemRemoved(STACKED_CARDS, A, DAY_0, FINISHED_ELSEWHERE, "1")
        verify(pixels).itemRemoved(STACKED_CARDS, B, DAY_0, COMPLETED, "0")
        assertNull(telemetryStore.state().ctaClickedItemId)
    }

    @Test
    fun `a second completion report of the same card sends nothing`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        testee.onCtaClicked(STACKED_CARDS, A)

        val reports = (1..2).map { async { testee.onItemsCompleted(STACKED_CARDS, setOf(A)) } }
        reports.awaitAll()

        verify(pixels, times(1)).itemRemoved(STACKED_CARDS, A, DAY_0, COMPLETED, "1")
        verify(pixels, times(1)).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        assertEquals(1, telemetryStore.state().completedItems)
    }

    @Test
    fun `dismissing a card sends its day count and clears its CTA attribution`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onCtaClicked(STACKED_CARDS, A)

        testee.onItemDismissed(A)

        verify(pixels).itemRemoved(STACKED_CARDS, A, DAY_0, DISMISSED, "1")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        assertNull(telemetryStore.state().ctaClickedItemId)
        assertEquals(mapOf(B to 0), telemetryStore.state().dayCounts)
    }

    @Test
    fun `all cards done sends the remaining day counts and the summary then resets`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B, C))
        testee.onCtaClicked(STACKED_CARDS, A)
        testee.onItemsCompleted(STACKED_CARDS, setOf(A))
        testee.onItemDismissed(B)
        testee.onItemsCompleted(STACKED_CARDS, setOf(C))

        testee.onAllItemsDone()
        testee.onActiveMessageChanged(null)

        verify(pixels).sectionEnded(
            STACKED_CARDS,
            DAY_0,
            "all_items_done",
            completed = 1,
            dismissed = 1,
            finishedElsewhere = 1,
            alreadyComplete = 0,
        )
        verify(pixels, times(1)).sectionEnded(any(), any(), any(), any(), anyOrNull(), any(), any())
        val state = telemetryStore.state()
        assertNull(state.displayedMessageId)
        assertEquals(emptyMap<String, Int>(), state.dayCounts)
        assertEquals(0, state.completedItems)
        assertEquals(0, state.dismissedItems)
        assertNull(state.firstRenderDay)
    }

    @Test
    fun `cards completed before the first impression are reported as already complete and counted apart from completions`() = runTest {
        testee.onItemsCompleted(STACKED_CARDS, setOf(A, B))
        verify(pixels).itemRemoved(STACKED_CARDS, A, DAY_0, ALREADY_COMPLETE, "0")
        verify(pixels).itemRemoved(STACKED_CARDS, B, DAY_0, ALREADY_COMPLETE, "0")
        verifyNoMoreInteractions(pixels)

        testee.onFrontCardShown("message", C, setOf(C), completedItemIds = setOf(A, B))
        testee.onItemDismissed(C)
        testee.onAllItemsDone()

        verify(pixels).sectionEnded(
            STACKED_CARDS,
            DAY_0,
            "all_items_done",
            completed = 0,
            dismissed = 1,
            finishedElsewhere = 0,
            alreadyComplete = 2,
        )
    }

    @Test
    fun `a later section counts items completed during an earlier one as already complete without reporting them again`() = runTest {
        testee.onCheckListShown("old", setOf(A, B, C))
        testee.onItemsCompleted(CHECK_LIST, setOf(A))
        testee.onSectionDismissed()

        testee.onCheckListShown("new", setOf(B, C), completedItemIds = setOf(A))
        testee.onSectionDismissed()

        verify(pixels, times(1)).itemRemoved(any(), any(), any(), any(), any())
        verify(pixels).sectionEnded(
            CHECK_LIST,
            DAY_0,
            "user_dismissed",
            completed = 0,
            dismissed = null,
            finishedElsewhere = 1,
            alreadyComplete = 0,
        )
        verify(pixels).sectionEnded(
            CHECK_LIST,
            DAY_0,
            "user_dismissed",
            completed = 0,
            dismissed = null,
            finishedElsewhere = 0,
            alreadyComplete = 1,
        )
    }

    @Test
    fun `the summary breaks completions down into CTA completions and those finished elsewhere`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B, C))
        testee.onCtaClicked(STACKED_CARDS, A)
        testee.onItemsCompleted(STACKED_CARDS, setOf(A, B))
        testee.onItemsCompleted(STACKED_CARDS, setOf(C))

        testee.onAllItemsDone()

        verify(pixels).sectionEnded(
            STACKED_CARDS,
            DAY_0,
            "all_items_done",
            completed = 1,
            dismissed = 0,
            finishedElsewhere = 2,
            alreadyComplete = 0,
        )
        val state = telemetryStore.state()
        assertEquals(0, state.finishedElsewhereItems)
        assertEquals(0, state.alreadyCompleteItems)
    }

    @Test
    fun `the first impression of a later day sends every previous day count including zeros`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B, C))
        today = today.plusDays(1)

        testee.onFrontCardShown("message", B, setOf(A, B, C))

        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, C, DAY_0, "0")
        testee.onCtaClicked(STACKED_CARDS, B)
        verify(pixels).itemCtaClicked(STACKED_CARDS, B, Days(activeDay = "1", calendarDay = "1"), "1")
        assertEquals(mapOf(A to 0, B to 1, C to 0), telemetryStore.state().dayCounts)
    }

    @Test
    fun `app resume on a later day sends the previous day counts without starting a new day`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        today = today.plusDays(1)

        testee.onAppResumed()

        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
        assertEquals(null, telemetryStore.state().openDay)
        assertEquals(1, telemetryStore.state().activeDays)

        testee.onFrontCardShown("message", A, setOf(A, B))

        testee.onCtaClicked(STACKED_CARDS, A)
        verify(pixels).itemCtaClicked(STACKED_CARDS, A, Days(activeDay = "1", calendarDay = "1"), "2")
        verify(pixels, times(1)).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels, times(1)).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
        assertEquals(mapOf(A to 1, B to 0), telemetryStore.state().dayCounts)
    }

    @Test
    fun `repeated app resumes over several days send the day counts once`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        today = today.plusDays(1)
        testee.onAppResumed()
        today = today.plusDays(1)
        testee.onAppResumed()
        today = today.plusDays(3)

        testee.onAppResumed()

        verify(pixels, times(2)).itemDailyImpressions(any(), any(), any(), any())
        assertEquals(1, telemetryStore.state().activeDays)
    }

    @Test
    fun `a render before the app resume on a later day leaves nothing for the resume to send`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        today = today.plusDays(1)
        testee.onFrontCardShown("message", A, setOf(A, B))

        testee.onAppResumed()

        verify(pixels, times(2)).itemDailyImpressions(any(), any(), any(), any())
        assertEquals(today.toEpochDay(), telemetryStore.state().openDay)
        assertEquals(mapOf(A to 1, B to 0), telemetryStore.state().dayCounts)
    }

    @Test
    fun `app resume on the open day or before any impression sends nothing`() = runTest {
        testee.onAppResumed()
        testee.onFrontCardShown("message", A, setOf(A, B))

        testee.onAppResumed()

        verify(pixels, never()).itemDailyImpressions(any(), any(), any(), any())
        assertEquals(firstDay.toEpochDay(), telemetryStore.state().openDay)
    }

    @Test
    fun `cards removed after the day was closed on resume count in the summary without a second day count`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        today = today.plusDays(1)
        testee.onAppResumed()

        testee.onItemsCompleted(STACKED_CARDS, setOf(A))
        testee.onItemDismissed(B)
        testee.onAllItemsDone()

        verify(pixels, times(1)).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels, times(1)).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
        verify(pixels, times(2)).itemDailyImpressions(any(), any(), any(), any())
        verify(pixels).sectionEnded(
            STACKED_CARDS,
            Days(activeDay = "0", calendarDay = "1"),
            "all_items_done",
            completed = 0,
            dismissed = 1,
            finishedElsewhere = 1,
            alreadyComplete = 0,
        )
    }

    @Test
    fun `active day counts only days with an impression while calendar day counts every day`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        today = today.plusDays(3)

        testee.onFrontCardShown("message", A, setOf(A))

        testee.onCtaClicked(STACKED_CARDS, A)
        verify(pixels).itemCtaClicked(STACKED_CARDS, A, Days(activeDay = "1", calendarDay = "3"), "2")
        assertEquals(2, telemetryStore.state().activeDays)
    }

    @Test
    fun `completion on a later day keeps the day count on the day it was counted`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        testee.onCtaClicked(STACKED_CARDS, A)
        today = today.plusDays(1)

        testee.onItemsCompleted(STACKED_CARDS, setOf(A))

        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels).itemRemoved(STACKED_CARDS, A, Days(activeDay = "0", calendarDay = "1"), COMPLETED, "1")
    }

    @Test
    fun `the message leaving the slot ends the carousel once with reason auto`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))

        testee.onActiveMessageChanged(null)
        testee.onActiveMessageChanged(null)

        verify(pixels, times(1)).sectionEnded(
            STACKED_CARDS,
            DAY_0,
            "auto",
            completed = 0,
            dismissed = 0,
            finishedElsewhere = 0,
            alreadyComplete = 0,
        )
        verify(pixels, times(1)).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels, times(1)).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
        assertNull(telemetryStore.state().displayedMessageId)
    }

    @Test
    fun `another message taking the slot ends the carousel with reason auto`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))

        testee.onActiveMessageChanged("other_message")

        verify(pixels).sectionEnded(
            STACKED_CARDS,
            DAY_0,
            "auto",
            completed = 0,
            dismissed = 0,
            finishedElsewhere = 0,
            alreadyComplete = 0,
        )
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
        testee.onItemDismissed(A)
        testee.onAllItemsDone()
        clearInvocations(pixels)

        testee.onActiveMessageChanged(null)

        verifyNoInteractions(pixels)
    }

    @Test
    fun `an impression for a new message sends the old days before resetting the day counters`() = runTest {
        testee.onFrontCardShown("old", A, setOf(A, B))
        today = today.plusDays(4)

        testee.onFrontCardShown("new", B, setOf(A, B))

        verify(pixels).sectionEnded(
            STACKED_CARDS,
            Days(activeDay = "0", calendarDay = "4-7"),
            "auto",
            completed = 0,
            dismissed = 0,
            finishedElsewhere = 0,
            alreadyComplete = 0,
        )
        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
        testee.onCtaClicked(STACKED_CARDS, B)
        verify(pixels).itemCtaClicked(STACKED_CARDS, B, DAY_0, "1")
        assertEquals(today.toEpochDay(), telemetryStore.state().firstRenderDay)
        assertEquals("new", telemetryStore.state().displayedMessageId)
        verify(pixels).sectionShownUnique("new", STACKED_CARDS)
    }

    @Test
    fun `state survives a restart`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A, B))
        testee.onCtaClicked(STACKED_CARDS, A)
        val restored = RealNextStepsTelemetry(RealNextStepsTelemetryStore(preferences), pixels, clock, coroutineRule.testDispatcherProvider)

        restored.onItemsCompleted(STACKED_CARDS, setOf(A))
        restored.onActiveMessageChanged(null)

        verify(pixels).itemRemoved(STACKED_CARDS, A, DAY_0, COMPLETED, "1")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
        verify(pixels).sectionEnded(
            STACKED_CARDS,
            DAY_0,
            "auto",
            completed = 1,
            dismissed = 0,
            finishedElsewhere = 0,
            alreadyComplete = 0,
        )
    }

    @Test
    fun `a clock moved backwards keeps counting into the open day`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        today = today.plusDays(2)
        testee.onFrontCardShown("message", A, setOf(A))
        clearInvocations(pixels)
        today = today.minusDays(1)

        testee.onFrontCardShown("message", A, setOf(A))

        testee.onCtaClicked(STACKED_CARDS, A)
        verify(pixels).itemCtaClicked(STACKED_CARDS, A, Days(activeDay = "1", calendarDay = "1"), "3-4")
        verify(pixels, never()).itemDailyImpressions(any(), any(), any(), any())
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
            testee.onCtaClicked(STACKED_CARDS, A)
            verify(pixels).itemCtaClicked(STACKED_CARDS, A, Days(activeDay = "0", calendarDay = expected), "1")
        }
    }

    @Test
    fun `active day buckets cover the boundaries`() = runTest {
        val boundaries = listOf(0 to "0", 1 to "0", 2 to "1", 4 to "3", 5 to "4-7", 8 to "4-7", 9 to "8-14", 15 to "8-14", 16 to "15+")
        for ((activeDays, expected) in boundaries) {
            telemetryStore.save(NextStepsTelemetryState(firstRenderDay = firstDay.toEpochDay(), activeDays = activeDays))
            clearInvocations(pixels)

            testee.onCtaClicked(STACKED_CARDS, A)

            verify(pixels).itemCtaClicked(STACKED_CARDS, A, Days(activeDay = expected, calendarDay = "0"), "0")
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
                    displayedVariant = STACKED_CARDS,
                    openDay = firstDay.toEpochDay(),
                    dayCounts = mapOf(A to count),
                ),
            )
            clearInvocations(pixels)

            testee.onActiveMessageChanged(null)

            verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, expected)
        }
    }

    @Test
    fun `an event before any impression reports day zero`() = runTest {
        testee.onCtaClicked(STACKED_CARDS, A)

        verify(pixels).itemCtaClicked(STACKED_CARDS, A, DAY_0, "0")
    }

    @Test
    fun `days without an impression produce no day counts`() = runTest {
        testee.onFrontCardShown("message", A, setOf(A))
        today = today.plusDays(3)

        testee.onActiveMessageChanged(null)

        verify(pixels, times(1)).itemDailyImpressions(any(), any(), any(), any())
        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
    }

    @Test
    fun `cta tap and removal carry the card's impressions over every day`() = runTest {
        repeat(2) { testee.onFrontCardShown("message", A, setOf(A, B)) }
        today = today.plusDays(1)
        testee.onFrontCardShown("message", A, setOf(A, B))

        testee.onCtaClicked(STACKED_CARDS, A)
        testee.onItemsCompleted(STACKED_CARDS, setOf(A))

        verify(pixels).itemCtaClicked(STACKED_CARDS, A, Days(activeDay = "1", calendarDay = "1"), "3-4")
        verify(pixels).itemRemoved(STACKED_CARDS, A, Days(activeDay = "1", calendarDay = "1"), COMPLETED, "3-4")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, Days(activeDay = "1", calendarDay = "1"), "1")
    }

    @Test
    fun `replacing the carousel on the same day starts at day zero`() = runTest {
        testee.onFrontCardShown("old", A, setOf(A, B))

        testee.onFrontCardShown("new", B, setOf(A, B))
        testee.onCtaClicked(STACKED_CARDS, B)

        verify(pixels).itemCtaClicked(STACKED_CARDS, B, DAY_0, "1")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels).itemDailyImpressions(STACKED_CARDS, B, DAY_0, "0")
        assertEquals(1, telemetryStore.state().activeDays)
        assertEquals(mapOf(A to 0, B to 1), telemetryStore.state().dayCounts)
    }

    @Test
    fun `a new carousel after ending and restarting on the same day starts at day zero`() = runTest {
        testee.onFrontCardShown("old", A, setOf(A))
        testee.onActiveMessageChanged(null)
        val restored = RealNextStepsTelemetry(RealNextStepsTelemetryStore(preferences), pixels, clock, coroutineRule.testDispatcherProvider)

        restored.onFrontCardShown("new", A, setOf(A))
        restored.onCtaClicked(STACKED_CARDS, A)

        verify(pixels).itemCtaClicked(STACKED_CARDS, A, DAY_0, "2")
        assertEquals(1, telemetryStore.state().activeDays)
        assertEquals(mapOf(A to 1), telemetryStore.state().dayCounts)
    }

    @Test
    fun `ending a carousel resets days and reports the new section across a restart`() = runTest {
        testee.onItemsCompleted(STACKED_CARDS, setOf(C))
        testee.onFrontCardShown("old", A, setOf(A), completedItemIds = setOf(C))
        today = today.plusDays(2)
        testee.onFrontCardShown("old", A, setOf(A))
        testee.onAllItemsDone()

        verify(pixels).sectionEnded(
            STACKED_CARDS,
            Days(activeDay = "1", calendarDay = "2"),
            "all_items_done",
            completed = 0,
            dismissed = 0,
            finishedElsewhere = 0,
            alreadyComplete = 1,
        )
        assertEquals(0, telemetryStore.state().activeDays)
        assertNull(telemetryStore.state().firstRenderDay)
        val restored = RealNextStepsTelemetry(RealNextStepsTelemetryStore(preferences), pixels, clock, coroutineRule.testDispatcherProvider)
        clearInvocations(pixels)
        today = today.plusDays(3)

        restored.onItemsCompleted(STACKED_CARDS, setOf(B, C))
        verify(pixels).itemRemoved(STACKED_CARDS, B, DAY_0, ALREADY_COMPLETE, "0")
        verifyNoMoreInteractions(pixels)
        restored.onFrontCardShown("new", A, setOf(A))
        restored.onCtaClicked(STACKED_CARDS, A)

        verify(pixels).sectionShownUnique("new", STACKED_CARDS)
        verify(pixels).itemCtaClicked(STACKED_CARDS, A, DAY_0, "3-4")
        today = today.plusDays(2)
        restored.onFrontCardShown("new", A, setOf(A))
        restored.onCtaClicked(STACKED_CARDS, A)
        verify(pixels).itemCtaClicked(STACKED_CARDS, A, Days(activeDay = "1", calendarDay = "2"), "3-4")
    }

    @Test
    fun `impressions keep counting across carousels`() = runTest {
        testee.onFrontCardShown("old", A, setOf(A))
        testee.onActiveMessageChanged(null)
        today = today.plusDays(1)
        testee.onFrontCardShown("new", A, setOf(A))

        testee.onItemDismissed(A)

        verify(pixels).itemRemoved(STACKED_CARDS, A, DAY_0, DISMISSED, "2")
    }

    @Test
    fun `impressions so far buckets cover the boundaries`() = runTest {
        for ((count, expected) in listOf(0 to "0", 1 to "1", 2 to "2", 3 to "3-4", 4 to "3-4", 5 to "5-7", 7 to "5-7", 8 to "8+", 20 to "8+")) {
            telemetryStore.save(NextStepsTelemetryState(firstRenderDay = firstDay.toEpochDay(), activeDays = 1, totalImpressions = mapOf(A to count)))
            clearInvocations(pixels)

            testee.onCtaClicked(STACKED_CARDS, A)

            verify(pixels).itemCtaClicked(STACKED_CARDS, A, DAY_0, expected)
        }
    }

    @Test
    fun `a check list render counts an impression for every open item and sends no daily counts`() = runTest {
        testee.onCheckListShown("message", setOf(A, B))
        today = today.plusDays(1)
        testee.onAppResumed()
        testee.onCheckListShown("message", setOf(A, B))

        testee.onCtaClicked(CHECK_LIST, A)
        testee.onSectionDismissed()

        verify(pixels).sectionShownUnique("message", CHECK_LIST)
        verify(pixels).itemCtaClicked(CHECK_LIST, A, Days(activeDay = "1", calendarDay = "1"), "2")
        verify(pixels).sectionEnded(
            CHECK_LIST,
            Days(activeDay = "1", calendarDay = "1"),
            "user_dismissed",
            completed = 0,
            dismissed = null,
            finishedElsewhere = 0,
            alreadyComplete = 0,
        )
        verify(pixels, never()).itemDailyImpressions(any(), any(), any(), any())
        assertNull(telemetryStore.state().displayedVariant)
    }

    @Test
    fun `check list completions carry their reason and count in the summary`() = runTest {
        testee.onCheckListShown("message", setOf(A, B, C))
        testee.onCtaClicked(CHECK_LIST, A)

        testee.onItemsCompleted(CHECK_LIST, setOf(A, B))
        testee.onSectionDismissed()

        verify(pixels).itemRemoved(CHECK_LIST, A, DAY_0, COMPLETED, "1")
        verify(pixels).itemRemoved(CHECK_LIST, B, DAY_0, FINISHED_ELSEWHERE, "1")
        verify(pixels).sectionEnded(
            CHECK_LIST,
            DAY_0,
            "user_dismissed",
            completed = 1,
            dismissed = null,
            finishedElsewhere = 1,
            alreadyComplete = 0,
        )
        verify(pixels, never()).itemDailyImpressions(any(), any(), any(), any())
    }

    @Test
    fun `an item already complete before the check list renders is reported with the check list variant`() = runTest {
        testee.onItemsCompleted(CHECK_LIST, setOf(A))
        testee.onCheckListShown("message", setOf(B), completedItemIds = setOf(A))
        testee.onSectionDismissed()

        verify(pixels).itemRemoved(CHECK_LIST, A, DAY_0, ALREADY_COMPLETE, "0")
        verify(pixels).sectionEnded(
            CHECK_LIST,
            DAY_0,
            "user_dismissed",
            completed = 0,
            dismissed = null,
            finishedElsewhere = 0,
            alreadyComplete = 1,
        )
    }

    @Test
    fun `the message leaving the slot ends the check list with reason auto across a restart`() = runTest {
        testee.onCheckListShown("message", setOf(A, B))
        val restored = RealNextStepsTelemetry(RealNextStepsTelemetryStore(preferences), pixels, clock, coroutineRule.testDispatcherProvider)

        restored.onActiveMessageChanged(null)

        verify(pixels).sectionEnded(
            CHECK_LIST,
            DAY_0,
            "auto",
            completed = 0,
            dismissed = null,
            finishedElsewhere = 0,
            alreadyComplete = 0,
        )
        verify(pixels, never()).itemDailyImpressions(any(), any(), any(), any())
        assertNull(telemetryStore.state().displayedMessageId)
    }

    @Test
    fun `state saved without a variant is reported as stacked cards`() = runTest {
        telemetryStore.save(
            NextStepsTelemetryState(
                firstRenderDay = firstDay.toEpochDay(),
                activeDays = 1,
                displayedMessageId = "message",
                openDay = firstDay.toEpochDay(),
                dayCounts = mapOf(A to 1),
            ),
        )

        testee.onActiveMessageChanged(null)

        verify(pixels).itemDailyImpressions(STACKED_CARDS, A, DAY_0, "1")
        verify(pixels).sectionEnded(
            STACKED_CARDS,
            DAY_0,
            "auto",
            completed = 0,
            dismissed = 0,
            finishedElsewhere = 0,
            alreadyComplete = 0,
        )
    }

    private companion object {
        const val A = "setup_default_browser"
        const val B = "setup_add_widget"
        const val C = "setup_sync"
        val DAY_0 = Days(activeDay = "0", calendarDay = "0")
    }
}
