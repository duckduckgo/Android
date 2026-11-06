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

package com.duckduckgo.app.browser.defaultbrowsing.prompts

import com.duckduckgo.app.nextsteps.NextStepsExperimentMetricSender
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

/**
 * Set-as-default clicks and conversions on the default browser prompt for the Next Steps experiment, so they can be compared
 * with those of the Next Steps default browser item.
 */
interface NextStepsSetAsDefaultPromptMetrics {
    suspend fun onPromptShown()

    suspend fun onPromptConfirmationClicked()

    /** DuckDuckGo became the default browser from the system dialog or default apps screen the prompt opened. */
    suspend fun onPromptDefaultBrowserSet()
}

@ContributesBinding(AppScope::class)
class RealNextStepsSetAsDefaultPromptMetrics @Inject constructor(
    private val sender: NextStepsExperimentMetricSender,
) : NextStepsSetAsDefaultPromptMetrics {

    override suspend fun onPromptShown() = sender.send("set_as_default_prompt_shown")

    override suspend fun onPromptConfirmationClicked() {
        sender.send(SET_AS_DEFAULT_CLICKED)
        sender.send("${SET_AS_DEFAULT_CLICKED}_prompt")
    }

    override suspend fun onPromptDefaultBrowserSet() {
        sender.send(DEFAULT_BROWSER_SET)
        sender.send("${DEFAULT_BROWSER_SET}_prompt")
    }

    private companion object {
        /** Shares its name with the Next Steps item metric, so a user who taps both counts once. */
        const val SET_AS_DEFAULT_CLICKED = "set_as_default_clicked"

        /** Shares its name with the Next Steps item metric, so a user who converts from both counts once. */
        const val DEFAULT_BROWSER_SET = "default_browser_set"
    }
}
