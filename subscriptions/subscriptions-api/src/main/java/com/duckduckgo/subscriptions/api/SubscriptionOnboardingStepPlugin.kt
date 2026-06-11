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

package com.duckduckgo.subscriptions.api

import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import com.duckduckgo.common.utils.plugins.ActivePlugin

/**
 * A single screen in the native subscription onboarding flow.
 *
 * Screens are contributed as plugins (from any module) so that each feature — VPN, Duck.ai, etc. —
 * can showcase itself. They are displayed in order: the [SubscriptionOnboardingStepType.INTRO]
 * first, then the [SubscriptionOnboardingStepType.STEP]s by plugin `priority`, then the
 * [SubscriptionOnboardingStepType.SUMMARY] last.
 *
 * The host screen is intentionally thin: it owns only the toolbar title (and confetti for INTRO).
 * The plugin owns its entire layout (header, content, buttons) and decides when the screen is
 * finished by calling [SubscriptionOnboardingStepNavigator].
 */
interface SubscriptionOnboardingStepPlugin : ActivePlugin {

    /** Stable id used to re-resolve this screen after fragment/process recreation and to persist progress. */
    val name: String

    /** Title shown in the host toolbar while this screen is active. */
    @get:StringRes
    val toolbarTitle: Int

    /**
     * Whether this is the intro screen, a real (counting) step, or the final summary. Only
     * [SubscriptionOnboardingStepType.STEP] screens count toward the completion percentage.
     */
    val stepType: SubscriptionOnboardingStepType
        get() = SubscriptionOnboardingStepType.STEP

    /**
     * The plugin's entire view: header, feature content and buttons. The plugin owns this layout,
     * any link navigation and any module-specific button actions. It fills the whole host body and
     * drives the flow through [navigator].
     */
    fun getOnboardingStepView(
        context: Context,
        navigator: SubscriptionOnboardingStepNavigator,
    ): View
}

enum class SubscriptionOnboardingStepType {
    /** Shown only when onboarding is entered after a purchase. Does NOT count toward completion. */
    INTRO,

    /** A real onboarding step. Counts toward the completion percentage. */
    STEP,

    /** The final summary screen showing total completion. Does NOT count toward completion. */
    SUMMARY,
}

/**
 * Passed by the host into each screen's view so the screen can drive the flow.
 */
interface SubscriptionOnboardingStepNavigator {

    /**
     * Mark the current step as completed (persisted across sessions) AND advance to the next screen.
     * Has no completion effect on INTRO/SUMMARY screens — it just advances.
     */
    fun onStepCompleted()

    /** Advance to the next screen WITHOUT marking the current step completed (e.g. a "skip" button). */
    fun onNextStep()

    /** Go back to the previous screen visited in this session (also triggered by the toolbar back arrow). */
    fun onBackStep()
}
