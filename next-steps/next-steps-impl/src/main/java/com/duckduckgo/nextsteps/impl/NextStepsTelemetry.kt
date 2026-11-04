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
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.RemovalReason
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.Variant
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the Next Steps pixel state shared by every NTP tab: the day anchor, the open-day and lifetime impression counters,
 * CTA attribution and which message and design are on screen.
 */
interface NextStepsTelemetry {
    /** [completedItemIds] is the completion snapshot at this render; a section starting now reports its size as already complete. */
    suspend fun onFrontCardShown(messageId: String, cardId: String, cardIdsInStack: Set<String>, completedItemIds: Set<String> = emptySet())

    /** The check list shows every open item at once, so one render counts an impression for each of them. */
    suspend fun onCheckListShown(messageId: String, openItemIds: Set<String>, completedItemIds: Set<String> = emptySet())

    suspend fun onCtaClicked(variant: Variant, itemId: String)

    /** [variant] is passed in because items already complete are reported before the section is first rendered. */
    suspend fun onItemsCompleted(variant: Variant, completedIds: Set<String>)

    /** Stacked cards only; the check list has no per-item dismissal. */
    suspend fun onItemDismissed(itemId: String)

    suspend fun onAllItemsDone()

    suspend fun onSectionDismissed()

    suspend fun onActiveMessageChanged(nextStepsMessageId: String?)

    /** Sends the previous day's counts without waiting for the next NTP render. */
    suspend fun onAppResumed()
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

    override suspend fun onFrontCardShown(messageId: String, cardId: String, cardIdsInStack: Set<String>, completedItemIds: Set<String>) {
        update { state, today ->
            state.display(messageId, Variant.STACKED_CARDS, today, cardIdsInStack, completedItemIds).incrementImpressions(cardId)
        }
    }

    override suspend fun onCheckListShown(messageId: String, openItemIds: Set<String>, completedItemIds: Set<String>) {
        update { state, today ->
            openItemIds.fold(state.display(messageId, Variant.CHECK_LIST, today, openItemIds, completedItemIds)) { displayed, itemId ->
                displayed.incrementImpressions(itemId)
            }
        }
    }

    override suspend fun onCtaClicked(variant: Variant, itemId: String) {
        update { state, today ->
            pixels.itemCtaClicked(variant, itemId, state.dayParams(today), state.impressionsSoFar(itemId))
            state.copy(ctaClickedItemId = itemId)
        }
    }

    override suspend fun onItemsCompleted(variant: Variant, completedIds: Set<String>) {
        update { state, today ->
            val reasons = completedIds.mapNotNull { itemId -> state.completionReason(itemId)?.let { itemId to it } }.toMap()
            reasons.forEach { (itemId, reason) ->
                pixels.itemRemoved(variant, itemId, state.dayParams(today), reason, state.impressionsSoFar(itemId))
            }
            state.removeItems(reasons).copy(reportedCompletedItems = state.reportedCompletedItems + completedIds)
        }
    }

    override suspend fun onItemDismissed(itemId: String) {
        update { state, today ->
            pixels.itemRemoved(Variant.STACKED_CARDS, itemId, state.dayParams(today), RemovalReason.DISMISSED, state.impressionsSoFar(itemId))
            state.removeItems(mapOf(itemId to RemovalReason.DISMISSED))
        }
    }

    override suspend fun onAllItemsDone() {
        update { state, today -> state.endSection(SectionEndReason.ALL_ITEMS_DONE, today) }
    }

    override suspend fun onSectionDismissed() {
        update { state, today -> state.endSection(SectionEndReason.USER_DISMISSED, today) }
    }

    override suspend fun onActiveMessageChanged(nextStepsMessageId: String?) {
        update { state, today -> state.endSectionIfReplaced(nextStepsMessageId, today) }
    }

    override suspend fun onAppResumed() {
        update { state, today -> state.sendCountsIfDayEnded(today) }
    }

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

    /**
     * The already-complete count comes from the render snapshot rather than from completion reports, because
     * an item reports its completion once per install and a later section would otherwise count it as zero.
     */
    private fun NextStepsTelemetryState.display(
        messageId: String,
        variant: Variant,
        today: Long,
        openItemIds: Set<String>,
        completedItemIds: Set<String>,
    ): NextStepsTelemetryState {
        val current = endSectionIfReplaced(messageId, today)
        if (current.firstRenderDay != null) {
            return current.copy(displayedMessageId = messageId, displayedVariant = variant).startNewDayIfChanged(today, openItemIds)
        }
        pixels.sectionShownUnique(messageId, variant)
        return current
            .copy(
                firstRenderDay = today,
                displayedMessageId = messageId,
                displayedVariant = variant,
                alreadyCompleteItems = completedItemIds.size,
            )
            .startNewDayIfChanged(today, openItemIds)
    }

    private fun NextStepsTelemetryState.endSectionIfReplaced(messageId: String?, today: Long): NextStepsTelemetryState =
        if (displayedMessageId != null && displayedMessageId != messageId) endSection(SectionEndReason.AUTO, today) else this

    /** On a new calendar day: sends the previous day's counts, then starts counting again with zero for every open item. */
    private fun NextStepsTelemetryState.startNewDayIfChanged(today: Long, openItemIds: Set<String>): NextStepsTelemetryState {
        val state = sendCountsIfDayEnded(today)
        if (state.openDay != null) return state
        return state.copy(
            activeDays = state.activeDays + 1,
            openDay = today,
            dayCounts = openItemIds.associateWith { 0 },
        )
    }

    /**
     * Sends the counts of a day that has ended, so they go out on app resume instead of waiting for the next render.
     * The items stay listed with zero so a removal before that render still counts in the summary, while
     * [sendDailyImpressions] sends nothing until a day is open again.
     */
    private fun NextStepsTelemetryState.sendCountsIfDayEnded(today: Long): NextStepsTelemetryState {
        val dayEnded = openDay != null && today > openDay
        return if (dayEnded) {
            sendDailyImpressions(dayCounts.keys)
            copy(openDay = null, dayCounts = dayCounts.mapValues { 0 })
        } else {
            this
        }
    }

    private fun NextStepsTelemetryState.completionReason(itemId: String): RemovalReason? = when {
        itemId in reportedCompletedItems -> null
        itemId == ctaClickedItemId -> RemovalReason.COMPLETED
        itemId in dayCounts -> RemovalReason.FINISHED_ELSEWHERE
        firstRenderDay == null -> RemovalReason.ALREADY_COMPLETE
        else -> null
    }

    private fun NextStepsTelemetryState.incrementImpressions(itemId: String): NextStepsTelemetryState = copy(
        dayCounts = dayCounts + (itemId to (dayCounts[itemId] ?: 0) + 1),
        totalImpressions = totalImpressions + (itemId to (totalImpressions[itemId] ?: 0) + 1),
    )

    private fun NextStepsTelemetryState.impressionsSoFar(itemId: String): String = impressionsBucket(totalImpressions[itemId] ?: 0)

    /** Items already complete were never on screen; the section counts them from its render snapshot instead. */
    private fun NextStepsTelemetryState.removeItems(reasons: Map<String, RemovalReason>): NextStepsTelemetryState {
        val removed = reasons.keys.filter { it in dayCounts }.toSet()
        sendDailyImpressions(removed)
        return copy(
            dayCounts = dayCounts - removed,
            ctaClickedItemId = ctaClickedItemId.takeUnless { it in removed },
            completedItems = completedItems + reasons.count { it.value == RemovalReason.COMPLETED },
            dismissedItems = dismissedItems + reasons.count { it.value == RemovalReason.DISMISSED },
            finishedElsewhereItems = finishedElsewhereItems + reasons.count { it.value == RemovalReason.FINISHED_ELSEWHERE },
        )
    }

    private fun NextStepsTelemetryState.endSection(reason: SectionEndReason, today: Long): NextStepsTelemetryState {
        if (displayedMessageId == null) return this
        sendDailyImpressions(dayCounts.keys)
        val variant = displayedVariantOrDefault()
        pixels.sectionEnded(
            variant,
            dayParams(today),
            reason.value,
            completedItems,
            dismissed = dismissedItems.takeIf { variant == Variant.STACKED_CARDS },
            finishedElsewhereItems,
            alreadyCompleteItems,
        )
        return copy(
            displayedMessageId = null,
            displayedVariant = null,
            firstRenderDay = null,
            activeDays = 0,
            ctaClickedItemId = null,
            openDay = null,
            dayCounts = emptyMap(),
            completedItems = 0,
            dismissedItems = 0,
            finishedElsewhereItems = 0,
            alreadyCompleteItems = 0,
        )
    }

    private fun NextStepsTelemetryState.sendDailyImpressions(itemIds: Set<String>) {
        val variant = displayedVariantOrDefault()
        if (variant != Variant.STACKED_CARDS) return
        val openDay = openDay ?: return
        for (itemId in itemIds) {
            val count = dayCounts[itemId] ?: continue
            pixels.itemDailyImpressions(variant, itemId, dayParams(openDay), impressionsBucket(count))
        }
    }

    /** State saved before the check list existed carries no variant; only the stacked cards could have been on screen. */
    private fun NextStepsTelemetryState.displayedVariantOrDefault(): Variant = displayedVariant ?: Variant.STACKED_CARDS

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

    private enum class SectionEndReason(val value: String) {
        ALL_ITEMS_DONE("all_items_done"),
        USER_DISMISSED("user_dismissed"),
        AUTO("auto"),
    }

    private fun impressionsBucket(count: Int): String = when (count) {
        in 0..2 -> count.toString()
        in 3..4 -> "3-4"
        in 5..7 -> "5-7"
        else -> "8+"
    }
}
