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
    CARD_IMPRESSION("next_steps_card_impression"),
    CARD_CTA_CLICKED("next_steps_card_cta_clicked"),
    CARD_COMPLETED("next_steps_card_completed"),
    CARD_DISMISSED("next_steps_card_dismissed"),
    CARD_DAILY_IMPRESSIONS("next_steps_card_daily_impressions"),
    CARDS_DISMISSED("next_steps_cards_dismissed"),
}

/** [activeDay] and [calendarDay] are the bucketed day values shared by every pixel; see [com.duckduckgo.nextsteps.impl.NextStepsTelemetryState]. */
interface NextStepsPixels {
    /** Once per install, the first time the stacked cards are rendered. */
    fun sectionShownUnique()
    fun cardImpression(card: String, days: Days)
    fun cardCtaClicked(card: String, days: Days)
    fun cardCompleted(card: String, days: Days)
    fun cardDismissed(card: String, days: Days)
    fun cardDailyImpressions(card: String, days: Days, impressions: String)
    fun cardsDismissed(days: Days, reason: String, completed: Int, dismissed: Int)

    data class Days(val activeDay: String, val calendarDay: String)
}

@ContributesBinding(AppScope::class)
class RealNextStepsPixels @Inject constructor(private val pixel: Pixel) : NextStepsPixels {
    override fun sectionShownUnique() = pixel.fire(NextStepsPixelName.SECTION_SHOWN_UNIQUE, type = Pixel.PixelType.Unique())

    override fun cardImpression(card: String, days: NextStepsPixels.Days) = fire(NextStepsPixelName.CARD_IMPRESSION, days, "card" to card)
    override fun cardCtaClicked(card: String, days: NextStepsPixels.Days) = fire(NextStepsPixelName.CARD_CTA_CLICKED, days, "card" to card)
    override fun cardCompleted(card: String, days: NextStepsPixels.Days) = fire(NextStepsPixelName.CARD_COMPLETED, days, "card" to card)
    override fun cardDismissed(card: String, days: NextStepsPixels.Days) = fire(NextStepsPixelName.CARD_DISMISSED, days, "card" to card)

    override fun cardDailyImpressions(card: String, days: NextStepsPixels.Days, impressions: String) {
        fire(NextStepsPixelName.CARD_DAILY_IMPRESSIONS, days, "card" to card, "impressions" to impressions)
    }

    override fun cardsDismissed(days: NextStepsPixels.Days, reason: String, completed: Int, dismissed: Int) {
        fire(NextStepsPixelName.CARDS_DISMISSED, days, "reason" to reason, "completed" to "$completed", "dismissed" to "$dismissed")
    }

    private fun fire(name: NextStepsPixelName, days: NextStepsPixels.Days, vararg parameters: Pair<String, String>) {
        val allParameters = mapOf(*parameters, "activeDay" to days.activeDay, "calendarDay" to days.calendarDay)
        pixel.fire(name, allParameters, type = Pixel.PixelType.Count)
    }
}
