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

package com.duckduckgo.app.onboarding.ui.page.configdriven.engine

import android.view.View
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaConfig
import com.duckduckgo.onboarding.api.LinearOnboardingStepId

/**
 * The decoration the embellishment controller settled on, after its fit check.
 */
data class SettledDecoration(
    val view: View,
    val anchorsCardOnPhone: Boolean,
    val anchoredCardBiasPhone: Float,
    val anchoredCardBiasTablet: Float,
)

interface ContentController {
    /** Hides every content include. Used before the first bind, when includes still sit at their XML defaults. */
    fun resetStage()

    fun bind(stepId: LinearOnboardingStepId, content: ContentConfig, scope: BindScope): ContentHandle

    fun hideBound()
}

/** The card choreography shared by every screen. Each call runs synchronously to its end state when not animating. */
interface CardStage {
    fun reveal(animate: Boolean, onEnd: () -> Unit)

    fun morph(animate: Boolean, onEnd: () -> Unit)

    /**
     * CTA text, visibility and click handling. A CTA fading in from alpha 0 cannot be clicked early even though
     * it is bound here: the card container swallows its children's touches for as long as an entrance runs.
     */
    fun showCtas(primary: CtaConfig?, secondary: CtaConfig?, onClick: (CtaConfig) -> Unit)

    /** Hides [contentTargets] and the visible CTAs so an entrance can fade them in. */
    fun prepareEntrance(contentTargets: List<View>)

    fun fadeInContent(contentTargets: List<View>, animate: Boolean, onEnd: () -> Unit)

    /** Ends whatever is in flight, running its continuation now rather than at its natural completion. */
    fun settle()

    fun release()
}
