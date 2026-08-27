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
    fun setDuckAiOnboardingFlow()
    fun isDuckAiOnboardingFlow(): Boolean
    fun setSegmentedOnboardingPath(path: SegmentedOnboardingPath?)

    /**
     * The segmented path the user is on, but only once that path has opted into the input screen. The
     * search path only does so if the user enabled the toggle; the AI path always does. Null everywhere
     * else, including a search path left without the toggle.
     */
    fun getSegmentedPathWithAiInput(): SegmentedOnboardingPath?
}

/**
 * The branch the user picked on the download reason step. Only enumerates a subset of paths that can have side effects on contextual CTAs.
 */
enum class SegmentedOnboardingPath {
    SEARCH,
    AI,
}
