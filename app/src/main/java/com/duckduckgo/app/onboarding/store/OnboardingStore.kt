/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.store

import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption

interface OnboardingStore {
    var onboardingDialogJourney: String?
    var linearPlanWidgetPromptShown: Boolean

    fun getSearchOptions(): List<DaxDialogIntroOption>
    fun getChatSuggestions(): List<DaxDialogIntroOption>
    fun getSitesOptions(): List<DaxDialogIntroOption>
    fun storeInputScreenSelection(selected: Boolean)
    fun getInputScreenSelection(): Boolean?
    fun isInputScreenSelectionOverriddenByUser(): Boolean
    fun setInputScreenSelectionOverriddenByUser()

    @Deprecated(
        message = "Migration only: the Duck.ai demo owns this state now.",
        replaceWith = ReplaceWith(
            expression = "DuckAiOnboardingDemo.isActive()",
            imports = ["com.duckduckgo.app.onboarding.DuckAiOnboardingDemo"],
        ),
    )
    fun isDuckAiOnboardingFlow(): Boolean

    fun setSegmentedSearchPathWithToggleEnabled(enabled: Boolean)
    fun isSegmentedSearchPathWithToggleEnabled(): Boolean
}
