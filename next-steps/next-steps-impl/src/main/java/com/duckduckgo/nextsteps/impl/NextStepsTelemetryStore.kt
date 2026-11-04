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

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.nextsteps.impl.pixels.NextStepsPixels.Variant
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

data class NextStepsTelemetryState(
    /** Epoch day of this section's first render; reset when the section ends. */
    val firstRenderDay: Long? = null,
    /** Calendar days on which this section was shown; `activeDay` in every pixel is this minus one. */
    val activeDays: Int = 0,
    val displayedMessageId: String? = null,
    /** Set together with [displayedMessageId]; pixels sent outside a render, such as the section end, report it. */
    val displayedVariant: Variant? = null,
    val ctaClickedItemId: String? = null,
    val openDay: Long? = null,
    /**
     * The items still open on screen, with their impressions today: front-card impressions for the stacked cards,
     * renders for the check list. Only the stacked cards send them as daily counts.
     */
    val dayCounts: Map<String, Int> = emptyMap(),
    /** Impressions per item over the whole install; never reset, so a CTA tap or removal can report how often the item was seen. */
    val totalImpressions: Map<String, Int> = emptyMap(),
    /** Items removed while the section was on screen, one count per removal reason; reported when it ends. */
    val completedItems: Int = 0,
    val dismissedItems: Int = 0,
    val finishedElsewhereItems: Int = 0,
    /** Items complete at this section's first render, including those completed during an earlier section. */
    val alreadyCompleteItems: Int = 0,
    /** Items whose completion was already reported; a second detection from another tab or after a restart sends nothing. */
    val reportedCompletedItems: Set<String> = emptySet(),
)

interface NextStepsTelemetryStore {
    fun state(): NextStepsTelemetryState

    fun save(state: NextStepsTelemetryState)
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealNextStepsTelemetryStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : NextStepsTelemetryStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = false, migrate = false)
    }

    override fun state() = NextStepsTelemetryState(
        firstRenderDay = preferences.getLong(KEY_FIRST_RENDER_DAY, 0).takeIf { preferences.contains(KEY_FIRST_RENDER_DAY) },
        activeDays = preferences.getInt(KEY_ACTIVE_DAYS, 0),
        displayedMessageId = preferences.getString(KEY_DISPLAYED_MESSAGE_ID, null),
        displayedVariant = preferences.getString(KEY_DISPLAYED_VARIANT, null)?.let { value -> Variant.entries.firstOrNull { it.value == value } },
        ctaClickedItemId = preferences.getString(KEY_CTA_CLICKED_ITEM_ID, null),
        openDay = preferences.getLong(KEY_OPEN_DAY, 0).takeIf { preferences.contains(KEY_OPEN_DAY) },
        dayCounts = preferences.counts(KEY_DAY_COUNTS),
        totalImpressions = preferences.counts(KEY_TOTAL_IMPRESSIONS),
        completedItems = preferences.getInt(KEY_COMPLETED_ITEMS, 0),
        dismissedItems = preferences.getInt(KEY_DISMISSED_ITEMS, 0),
        finishedElsewhereItems = preferences.getInt(KEY_FINISHED_ELSEWHERE_ITEMS, 0),
        alreadyCompleteItems = preferences.getInt(KEY_ALREADY_COMPLETE_ITEMS, 0),
        reportedCompletedItems = preferences.getStringSet(KEY_REPORTED_COMPLETED_ITEMS, null).orEmpty(),
    )

    override fun save(state: NextStepsTelemetryState) {
        preferences.edit {
            state.firstRenderDay?.let { putLong(KEY_FIRST_RENDER_DAY, it) } ?: remove(KEY_FIRST_RENDER_DAY)
            putInt(KEY_ACTIVE_DAYS, state.activeDays)
            putString(KEY_DISPLAYED_MESSAGE_ID, state.displayedMessageId)
            putString(KEY_DISPLAYED_VARIANT, state.displayedVariant?.value)
            putString(KEY_CTA_CLICKED_ITEM_ID, state.ctaClickedItemId)
            state.openDay?.let { putLong(KEY_OPEN_DAY, it) } ?: remove(KEY_OPEN_DAY)
            putCounts(KEY_DAY_COUNTS, state.dayCounts)
            putCounts(KEY_TOTAL_IMPRESSIONS, state.totalImpressions)
            putInt(KEY_COMPLETED_ITEMS, state.completedItems)
            putInt(KEY_DISMISSED_ITEMS, state.dismissedItems)
            putInt(KEY_FINISHED_ELSEWHERE_ITEMS, state.finishedElsewhereItems)
            putInt(KEY_ALREADY_COMPLETE_ITEMS, state.alreadyCompleteItems)
            putStringSet(KEY_REPORTED_COMPLETED_ITEMS, state.reportedCompletedItems)
        }
    }

    private fun SharedPreferences.counts(key: String): Map<String, Int> = getStringSet(key, null).orEmpty().associate { entry ->
        entry.substringBeforeLast(":") to entry.substringAfterLast(":").toInt()
    }

    private fun SharedPreferences.Editor.putCounts(key: String, counts: Map<String, Int>) {
        putStringSet(key, counts.map { (id, count) -> "$id:$count" }.toSet())
    }

    private companion object {
        const val FILENAME = "com.duckduckgo.nextsteps.telemetry"
        const val KEY_FIRST_RENDER_DAY = "KEY_FIRST_RENDER_DAY"
        const val KEY_ACTIVE_DAYS = "KEY_ACTIVE_DAYS"
        const val KEY_DISPLAYED_MESSAGE_ID = "KEY_DISPLAYED_MESSAGE_ID"
        const val KEY_DISPLAYED_VARIANT = "KEY_DISPLAYED_VARIANT"
        const val KEY_CTA_CLICKED_ITEM_ID = "KEY_CTA_CLICKED_CARD_ID"
        const val KEY_OPEN_DAY = "KEY_OPEN_DAY"
        const val KEY_DAY_COUNTS = "KEY_DAY_COUNTS"
        const val KEY_TOTAL_IMPRESSIONS = "KEY_TOTAL_IMPRESSIONS"
        const val KEY_COMPLETED_ITEMS = "KEY_COMPLETED_CARDS"
        const val KEY_DISMISSED_ITEMS = "KEY_DISMISSED_CARDS"
        const val KEY_FINISHED_ELSEWHERE_ITEMS = "KEY_FINISHED_ELSEWHERE_ITEMS"
        const val KEY_ALREADY_COMPLETE_ITEMS = "KEY_ALREADY_COMPLETE_ITEMS"
        const val KEY_REPORTED_COMPLETED_ITEMS = "KEY_REPORTED_COMPLETED_CARDS"
    }
}
