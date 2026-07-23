/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.StepProgress
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundStep

/** Which animated stage decoration accompanies the dialog. Fit veto may still hide it at runtime. */
enum class Embellishment { WalkingDax, BobbingDax, BottomWing, LeftWing, None }

sealed interface CtaAction {
    /** CTA click forwards this event to the orchestrator as-is. */
    data class Emit(val event: NewUserOnboardingEvent) : CtaAction

    /** CTA click asks the bound screen's [ContentHandle.result] to build the event from live state. */
    data object Submit : CtaAction
}

data class CtaConfig(
    val text: TextConfig,
    val action: CtaAction,
)

data class DialogConfig(
    val background: OnboardingBackgroundStep,
    val embellishment: Embellishment,
    val content: ContentConfig,
    /** Null when the screen has no primary CTA (e.g. input screen preview progresses by submitting a query). */
    val primaryCta: CtaConfig? = null,
    val secondaryCta: CtaConfig? = null,
    val stepIndicator: StepProgress? = null,
)
