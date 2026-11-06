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
package com.duckduckgo.nextsteps.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

enum class NextStepsPixelName(override val pixelName: String) : Pixel.PixelName {
    SECTION_SHOWN_UNIQUE("next_steps_section_shown_unique"),
    ITEM_CTA_CLICKED("next_steps_item_cta_clicked"),
    ITEM_REMOVED("next_steps_item_removed"),
    ITEM_DAILY_IMPRESSIONS("next_steps_item_daily_impressions"),
    SECTION_ENDED("next_steps_section_ended"),
}

/**
 * [activeDay] and [calendarDay] are the bucketed day values shared by every pixel; see [com.duckduckgo.nextsteps.impl.NextStepsTelemetryState].
 * `impressionsSoFar` is the bucketed number of times the item was on screen on this install, so a per-impression pixel is not needed.
 */
interface NextStepsPixels {
    /** Once per RMF message ID per install; the message ID is used only for local deduplication. */
    fun sectionShownUnique(messageId: String, variant: Variant)
    fun itemCtaClicked(variant: Variant, item: String, days: Days, impressionsSoFar: String)
    fun itemRemoved(variant: Variant, item: String, days: Days, reason: RemovalReason, impressionsSoFar: String)

    /** Stacked cards only: the check list shows every item at once, so it has no per-item impressions to roll up. */
    fun itemDailyImpressions(variant: Variant, item: String, days: Days, impressions: String)

    /**
     * One count per removal reason, so they add up to the items that left the section; [completed] is CTA completions only.
     * [dismissed] is stacked cards only and omitted when null, since the check list has no per-item dismissal.
     */
    fun sectionEnded(
        variant: Variant,
        days: Days,
        reason: String,
        completed: Int,
        dismissed: Int?,
        finishedElsewhere: Int,
        alreadyComplete: Int,
    )

    data class Days(val activeDay: String, val calendarDay: String)

    /** The section design the user saw; matches the experiment cohort names. */
    enum class Variant(val value: String) {
        STACKED_CARDS("stacked_cards"),
        CHECK_LIST("check_list"),
    }

    enum class RemovalReason(val value: String) {
        /** Detected complete after its CTA was the last one tapped. */
        COMPLETED("completed"),

        /** Closed with the X; stacked cards only. */
        DISMISSED("dismissed"),

        /** Detected complete while on screen, without a CTA tap on it. */
        FINISHED_ELSEWHERE("finished_elsewhere"),

        /** Detected complete before the current section first rendered. */
        ALREADY_COMPLETE("already_complete"),
    }
}

@ContributesBinding(AppScope::class)
class RealNextStepsPixels @Inject constructor(private val pixel: Pixel) : NextStepsPixels {
    override fun sectionShownUnique(messageId: String, variant: NextStepsPixels.Variant) {
        pixel.fire(
            NextStepsPixelName.SECTION_SHOWN_UNIQUE,
            mapOf(VARIANT to variant.value),
            type = Pixel.PixelType.Unique(tag = "${NextStepsPixelName.SECTION_SHOWN_UNIQUE.pixelName}_$messageId"),
        )
    }

    override fun itemCtaClicked(variant: NextStepsPixels.Variant, item: String, days: NextStepsPixels.Days, impressionsSoFar: String) {
        fire(NextStepsPixelName.ITEM_CTA_CLICKED, variant, days, ITEM to item, IMPRESSIONS_SO_FAR to impressionsSoFar)
    }

    override fun itemRemoved(
        variant: NextStepsPixels.Variant,
        item: String,
        days: NextStepsPixels.Days,
        reason: NextStepsPixels.RemovalReason,
        impressionsSoFar: String,
    ) {
        fire(NextStepsPixelName.ITEM_REMOVED, variant, days, ITEM to item, "reason" to reason.value, IMPRESSIONS_SO_FAR to impressionsSoFar)
    }

    override fun itemDailyImpressions(variant: NextStepsPixels.Variant, item: String, days: NextStepsPixels.Days, impressions: String) {
        fire(NextStepsPixelName.ITEM_DAILY_IMPRESSIONS, variant, days, ITEM to item, "impressions" to impressions)
    }

    override fun sectionEnded(
        variant: NextStepsPixels.Variant,
        days: NextStepsPixels.Days,
        reason: String,
        completed: Int,
        dismissed: Int?,
        finishedElsewhere: Int,
        alreadyComplete: Int,
    ) {
        val parameters = listOfNotNull(
            "reason" to reason,
            "completed" to "$completed",
            dismissed?.let { "dismissed" to "$it" },
            "finishedElsewhere" to "$finishedElsewhere",
            "alreadyComplete" to "$alreadyComplete",
        )
        fire(NextStepsPixelName.SECTION_ENDED, variant, days, *parameters.toTypedArray())
    }

    private fun fire(
        name: NextStepsPixelName,
        variant: NextStepsPixels.Variant,
        days: NextStepsPixels.Days,
        vararg parameters: Pair<String, String>,
    ) {
        val allParameters = mapOf(VARIANT to variant.value, *parameters, "activeDay" to days.activeDay, "calendarDay" to days.calendarDay)
        pixel.fire(name, allParameters, type = Pixel.PixelType.Count)
    }

    private companion object {
        const val VARIANT = "variant"
        const val ITEM = "item"
        const val IMPRESSIONS_SO_FAR = "impressionsSoFar"
    }
}
