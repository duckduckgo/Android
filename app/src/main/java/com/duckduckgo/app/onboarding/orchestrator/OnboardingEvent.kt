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

package com.duckduckgo.app.onboarding.orchestrator

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.onboarding.api.LinearOnboardingEvent

sealed interface OnboardingEvent : LinearOnboardingEvent {
    data object PrimaryClicked : OnboardingEvent
    data object SecondaryClicked : OnboardingEvent
    data object SkipOnboardingDevOptionClicked : OnboardingEvent
    data class DuckAiPromptSubmitted(val prompt: String) : OnboardingEvent
    data object DuckAiFireCompleted : OnboardingEvent
    data class DefaultBrowserPromptFinished(val isDefaultBrowser: Boolean) : OnboardingEvent
    data class OmnibarTypeSelected(val type: OmnibarType) : OnboardingEvent
}
