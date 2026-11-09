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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.send
import dev.zacsweers.metro.ContributesBinding
import logcat.logcat
import javax.inject.Inject

/**
 * Experiment metrics for the Next Steps section, kept apart from the classic pixels because they only live as long as
 * the experiment. Each metric reaches the backend at most once per conversion window, so callers can report every event.
 */
interface NextStepsExperimentMetrics {
    /** Check list only: it shows every item at once, so one section metric stands in for the item ones. */
    suspend fun onSectionShown()

    /** Stacked cards only: just the front card is visible, so each card reports its own exposure. */
    suspend fun onItemShown(itemId: String)

    suspend fun onItemCtaClicked(itemId: String)

    /** CTA completions only; items finished elsewhere or already complete are not counted. */
    suspend fun onItemCompleted(itemId: String)
}

@ContributesBinding(AppScope::class)
class RealNextStepsExperimentMetrics @Inject constructor(
    private val toggles: NextStepsItemsExperimentToggles,
) : NextStepsExperimentMetrics {

    override suspend fun onSectionShown() = send("next_steps_section_shown")

    override suspend fun onItemShown(itemId: String) = send("next_steps_item_shown_$itemId")

    override suspend fun onItemCtaClicked(itemId: String) {
        send("next_steps_item_cta_clicked_$itemId")
        if (itemId == DEFAULT_BROWSER_ITEM_ID) send(SET_AS_DEFAULT_CLICKED)
    }

    override suspend fun onItemCompleted(itemId: String) {
        send("next_steps_item_completed_$itemId")
        if (itemId == DEFAULT_BROWSER_ITEM_ID) send(DEFAULT_BROWSER_SET)
    }

    private suspend fun send(metric: String) {
        // The sender drops repeats within a window, so this logs every request rather than what reaches the backend.
        logcat(tag = LOG_TAG) { "send $metric" }
        MetricsPixel(
            metric = metric,
            type = MetricType.NORMAL,
            value = "1",
            toggle = toggles.nextStepsItemsExperiment(),
            conversionWindow = listOf(
                ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ConversionWindow(lowerWindow = 0, upperWindow = 7),
            ),
        ).send()
    }

    private companion object {
        const val LOG_TAG = "NextStepsMetrics"
        const val DEFAULT_BROWSER_ITEM_ID = "setup_default_browser"

        /** Also sent by the default browser prompt in the app module; the same name and windows let a user who taps both count once. */
        const val SET_AS_DEFAULT_CLICKED = "set_as_default_clicked"

        /** Also sent by the default browser prompt in the app module; the same name and windows let a user who converts from both count once. */
        const val DEFAULT_BROWSER_SET = "default_browser_set"
    }
}
