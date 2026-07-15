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

/**
 * What the [com.duckduckgo.app.onboarding.ui.OnboardingActivity] renderer should present for the current step.
 */
sealed interface NewUserOnboardingActivityDialog {
    data class IntroAnimation(val withDuckAi: Boolean = false) : NewUserOnboardingActivityDialog
    data object NotificationPermission : NewUserOnboardingActivityDialog
    data object SyncRestore : NewUserOnboardingActivityDialog
    data object InitialReinstallUser : NewUserOnboardingActivityDialog
    data object Initial : NewUserOnboardingActivityDialog
    data object ComparisonChart : NewUserOnboardingActivityDialog
    data object AiComparisonChart : NewUserOnboardingActivityDialog
    data object DefaultBrowserPrompt : NewUserOnboardingActivityDialog
    data class AddressBarPosition(val showSplitOption: Boolean) : NewUserOnboardingActivityDialog
    data object InputScreen : NewUserOnboardingActivityDialog
    data class InputScreenPreview(val isSearchDefault: Boolean) : NewUserOnboardingActivityDialog
    data class QuickSetup(
        val showSplitOption: Boolean,
        val hideSetDefaultBrowserRow: Boolean,
        val hideAddWidgetRow: Boolean,
        val isReinstallUser: Boolean,
    ) : NewUserOnboardingActivityDialog
}
