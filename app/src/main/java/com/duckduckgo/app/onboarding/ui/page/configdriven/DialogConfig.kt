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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.StepProgress
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackground

/**
 * Everything that makes one onboarding dialog different from another. Plain, value-comparable data: equality
 * drives the render engine's diff, so it must never hold views or lambdas over view state.
 */
data class DialogConfig(
    val background: OnboardingBackground,
    val embellishment: Embellishment = Embellishment.None,
    val cardArrow: CardArrowConfig = CardArrowConfig.Hidden,
    val cardEntry: CardEntry = CardEntry.Immediate,
    val content: ContentConfig,
    val primaryCta: CtaConfig? = null,
    val secondaryCta: CtaConfig? = null,
    val stepIndicator: StepProgress? = null,
)

/** The animated stage decoration accompanying a dialog. A runtime fit check may still hide it. */
enum class Embellishment { WalkingDax, BobbingDax, BottomWing, LeftWing, RightWing, None }

/**
 * Where the card's bubble tail sits, and which way it hooks. The tail is asymmetric, so a screen whose
 * artwork sits opposite the default hook needs a mirrored variant rather than just a different position.
 */
enum class CardArrowConfig(
    val atEnd: Boolean,
    val mirrored: Boolean,
) {
    Hidden(atEnd = false, mirrored = false),
    AtStart(atEnd = false, mirrored = false),
    AtEnd(atEnd = true, mirrored = false),
    AtStartMirrored(atEnd = false, mirrored = true),
}

/** When the card's one-time fade-in starts. [AfterBackgroundTransition] holds it back until an animated background transition has finished. */
enum class CardEntry { Immediate, AfterBackgroundTransition }

data class CtaConfig(
    val text: TextConfig,
    val action: CtaAction,
)

sealed interface CtaAction {

    /** Forwards [event] to the orchestrator as-is. */
    data class Emit(val event: NewUserOnboardingEvent) : CtaAction

    /** Asks the bound screen to build the event from its live state, via [ContentHandle.result]. */
    data object Submit : CtaAction
}
