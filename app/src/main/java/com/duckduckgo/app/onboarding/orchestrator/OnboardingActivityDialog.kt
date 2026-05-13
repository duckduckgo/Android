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

sealed interface OnboardingActivityDialog {
    data object IntroAnimation : OnboardingActivityDialog
    data object InitialReinstallUser : OnboardingActivityDialog
    data object Initial : OnboardingActivityDialog
    data class InputScreenPreview(val isSearchDefault: Boolean) : OnboardingActivityDialog
    data object ComparisonChart : OnboardingActivityDialog

    // Not a Dax dialog — represents the OS default-browser system prompt. The
    // VM constructs and launches the intent when this becomes current; the
    // fragment never renders a Dax surface for it.
    data object DefaultBrowserPrompt : OnboardingActivityDialog

    data class AddressBarPosition(val showSplitOption: Boolean) : OnboardingActivityDialog
    data object SkipOnboardingOption : OnboardingActivityDialog
}
