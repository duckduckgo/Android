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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the Next Steps pixel state shared by every NTP tab: the day anchor, the open-day impression counters,
 * CTA attribution and which message is on screen. All transitions run under one lock so two tabs
 * reporting the same event produce one pixel.
 */
interface NextStepsTelemetry {
    suspend fun onFrontCardShown(messageId: String, cardId: String, cardIdsInStack: Set<String>)

    suspend fun onCtaClicked(cardId: String)

    suspend fun onCardsCompleted(completedIds: Set<String>)

    suspend fun onCardDismissed(cardId: String)

    suspend fun onAllCardsDone()

    suspend fun onActiveMessageChanged(nextStepsMessageId: String?)
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealNextStepsTelemetry @Inject constructor(
    private val telemetryStore: NextStepsTelemetryStore,
    private val pixels: NextStepsPixels,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatchers: DispatcherProvider,
) : NextStepsTelemetry {
    private val mutex = Mutex()

    override suspend fun onFrontCardShown(messageId: String, cardId: String, cardIdsInStack: Set<String>) {
        update { state, today ->
            if (state.firstRenderDay == null) pixels.sectionShownUnique()
            val displayed = state
                .endCarouselIfReplaced(messageId, today)
                .markCarouselDisplayed(messageId, today)
                .startNewDayIfChanged(today, cardIdsInStack)
            pixels.cardImpression(cardId, displayed.dayParams(today))
            displayed.incrementImpressions(cardId)
        }
    }

    override suspend fun onCtaClicked(cardId: String) {
        update { state, today ->
            pixels.cardCtaClicked(cardId, state.dayParams(today))
            state.copy(ctaClickedCardId = cardId)
        }
    }

    override suspend fun onCardsCompleted(completedIds: Set<String>) {
        update { state, today ->
            state.ctaClickedCardId?.takeIf { it in completedIds }?.let { pixels.cardCompleted(it, state.dayParams(today)) }
            state.removeCards(completedIds, Removal.COMPLETED)
        }
    }

    override suspend fun onCardDismissed(cardId: String) {
        update { state, today ->
            pixels.cardDismissed(cardId, state.dayParams(today))
            state.removeCards(setOf(cardId), Removal.DISMISSED)
        }
    }

    override suspend fun onAllCardsDone() {
        update { state, today -> state.endCarousel(DismissReason.ALL_CARDS_DONE, today) }
    }

    override suspend fun onActiveMessageChanged(nextStepsMessageId: String?) {
        update { state, today -> state.endCarouselIfReplaced(nextStepsMessageId, today) }
    }

    /** Runs one state transition under the lock; [Pixel.fire][com.duckduckgo.app.statistics.pixels.Pixel.fire] only enqueues, so firing inside it is fine. */
    private suspend fun update(transition: (state: NextStepsTelemetryState, today: Long) -> NextStepsTelemetryState) {
        withContext(dispatchers.io()) {
            mutex.withLock {
                val today = currentTimeProvider.localDateTimeNow().toLocalDate().toEpochDay()
                val before = telemetryStore.state()
                val after = transition(before, today)
                if (after != before) {
                    telemetryStore.save(after)
                }
            }
        }
    }

    private fun NextStepsTelemetryState.endCarouselIfReplaced(messageId: String?, today: Long): NextStepsTelemetryState =
        if (displayedMessageId != null && displayedMessageId != messageId) endCarousel(DismissReason.AUTO, today) else this

    private fun NextStepsTelemetryState.markCarouselDisplayed(messageId: String, today: Long): NextStepsTelemetryState =
        copy(firstRenderDay = firstRenderDay ?: today, displayedMessageId = messageId)

    /** On a new calendar day: sends the previous day's counts, then starts counting again with zero for every card in the stack. */
    private fun NextStepsTelemetryState.startNewDayIfChanged(today: Long, cardIdsInStack: Set<String>): NextStepsTelemetryState {
        val openDay = openDay
        if (openDay != null && today <= openDay) return this
        if (openDay != null) sendDailyImpressions(dayCounts.keys)
        return copy(activeDays = activeDays + 1, openDay = today, dayCounts = cardIdsInStack.associateWith { 0 })
    }

    private fun NextStepsTelemetryState.incrementImpressions(cardId: String): NextStepsTelemetryState =
        copy(dayCounts = dayCounts + (cardId to (dayCounts[cardId] ?: 0) + 1))

    /**
     * Sends the day count of each card now, before it drops out of the carousel. Only cards still counted are
     * affected, so a repeated report, or a card completed before it was ever shown, changes nothing.
     */
    private fun NextStepsTelemetryState.removeCards(cardIds: Set<String>, removal: Removal): NextStepsTelemetryState {
        val removed = cardIds.filter { it in dayCounts }.toSet()
        sendDailyImpressions(removed)
        return copy(
            dayCounts = dayCounts - removed,
            ctaClickedCardId = ctaClickedCardId.takeUnless { it in removed },
            completedCards = completedCards + if (removal == Removal.COMPLETED) removed.size else 0,
            dismissedCards = dismissedCards + if (removal == Removal.DISMISSED) removed.size else 0,
        )
    }

    private fun NextStepsTelemetryState.endCarousel(reason: DismissReason, today: Long): NextStepsTelemetryState {
        if (displayedMessageId == null) return this
        sendDailyImpressions(dayCounts.keys)
        pixels.cardsDismissed(dayParams(today), reason.value, completedCards, dismissedCards)
        return copy(
            displayedMessageId = null,
            ctaClickedCardId = null,
            openDay = null,
            dayCounts = emptyMap(),
            completedCards = 0,
            dismissedCards = 0,
        )
    }

    private fun NextStepsTelemetryState.sendDailyImpressions(cardIds: Set<String>) {
        val openDay = openDay ?: return
        for (cardId in cardIds) {
            val count = dayCounts[cardId] ?: continue
            pixels.cardDailyImpressions(cardId, dayParams(openDay), impressionsBucket(count))
        }
    }

    private fun NextStepsTelemetryState.dayParams(eventDay: Long) = NextStepsPixels.Days(
        activeDay = dayBucket(activeDays - 1L),
        calendarDay = dayBucket(eventDay - (firstRenderDay ?: eventDay)),
    )

    private fun dayBucket(day: Long): String = when (val clamped = day.coerceAtLeast(0)) {
        in 0..3 -> clamped.toString()
        in 4..7 -> "4-7"
        in 8..14 -> "8-14"
        else -> "15+"
    }

    private enum class Removal { COMPLETED, DISMISSED }

    private enum class DismissReason(val value: String) {
        ALL_CARDS_DONE("all_cards_done"),
        AUTO("auto"),
    }

    private fun impressionsBucket(count: Int): String = when (count) {
        in 0..2 -> count.toString()
        in 3..4 -> "3-4"
        in 5..7 -> "5-7"
        else -> "8+"
    }
}
