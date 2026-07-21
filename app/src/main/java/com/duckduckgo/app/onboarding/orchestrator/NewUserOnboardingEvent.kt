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

/**
 * User actions on the [NewUserOnboardingPlanProvider].
 */
sealed interface NewUserOnboardingEvent : LinearOnboardingEvent {
    data object Presented : NewUserOnboardingEvent
    data object IntroAnimationFinished : NewUserOnboardingEvent
    data class NotificationPermissionFinished(val granted: Boolean?) : NewUserOnboardingEvent
    data object ContinueClicked : NewUserOnboardingEvent
    data object RestoreRequested : NewUserOnboardingEvent
    data object SkipRequested : NewUserOnboardingEvent
    data class DefaultBrowserPromptFinished(val isDefaultBrowser: Boolean) : NewUserOnboardingEvent
    data class AddressBarConfirmed(val type: OmnibarType) : NewUserOnboardingEvent
    data class InputModeConfirmed(val withAi: Boolean) : NewUserOnboardingEvent
    data class QuickSetupConfirmed(val type: OmnibarType, val withAi: Boolean) : NewUserOnboardingEvent
    data class InputDemoQuerySubmitted(val query: String, val isChat: Boolean, val fromSuggestion: Boolean) : NewUserOnboardingEvent

    /** The in-browser Duck.ai onboarding demo's fire flow completed; advances the duck_ai_demo step. */
    data object DuckAiFireCompleted : NewUserOnboardingEvent

    /**
     * Internal-only "skip all onboarding" dev shortcut. Not a dialog action: every step's transition
     * is wrapped to treat it as [com.duckduckgo.onboarding.api.LinearOnboardingTransition.AbortPlan],
     * so it terminates the run to Skipped from wherever we are.
     * See [NewUserOnboardingPlanProvider.abortingOnDevSkip].
     */
    data object SkipNewUserOnboardingDevOptionClicked : NewUserOnboardingEvent

    data object AddWidgetRequested : NewUserOnboardingEvent

    data object WidgetPromptSkipped : NewUserOnboardingEvent

    data class AddWidgetFinished(val widgetAdded: Boolean) : NewUserOnboardingEvent
}
