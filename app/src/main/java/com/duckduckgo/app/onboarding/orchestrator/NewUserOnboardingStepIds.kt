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

import com.duckduckgo.onboarding.api.LinearOnboardingStepId

/**
 * Stable identifiers for the steps composed by [NewUserOnboardingPlanProvider].
 */
internal object NewUserOnboardingStepIds {
    const val INTRO_ANIMATION: LinearOnboardingStepId = "intro_animation"
    const val NOTIFICATION_PERMISSION: LinearOnboardingStepId = "notification_permission"
    const val SYNC_RESTORE: LinearOnboardingStepId = "sync_restore"
    const val INITIAL_REINSTALL_USER: LinearOnboardingStepId = "initial_reinstall_user"
    const val INITIAL: LinearOnboardingStepId = "initial"
    const val COMPARISON_CHART: LinearOnboardingStepId = "comparison_chart"
    const val AI_COMPARISON_CHART: LinearOnboardingStepId = "ai_comparison_chart"
    const val DUCK_AI_DEMO: LinearOnboardingStepId = "duck_ai_demo"
    const val DEFAULT_BROWSER_PROMPT: LinearOnboardingStepId = "default_browser_prompt"
    const val WIDGET_PROMPT: LinearOnboardingStepId = "widget_prompt"
    const val ADD_WIDGET: LinearOnboardingStepId = "add_widget"
    const val ADDRESS_BAR_POSITION: LinearOnboardingStepId = "address_bar_position"
    const val INPUT_SCREEN: LinearOnboardingStepId = "input_screen"
    const val INPUT_SCREEN_PREVIEW: LinearOnboardingStepId = "input_screen_preview"
    const val QUICK_SETUP: LinearOnboardingStepId = "quick_setup"
}
