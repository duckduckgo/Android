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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.Flow

/**
 * A single step in the native subscription onboarding flow, contributed from the
 * feature module that owns the step
 */
interface SubscriptionOnboardingStepPlugin {
    /** Stable id for this step */
    val stepId: String

    /** Title shown in the host's toolbar while this step is on screen, or null for no toolbar title. */
    @get:StringRes
    val titleResId: Int? get() = null

    /** The row this step contributes to the onboarding completion summary list */
    val completionSummaryRow: SubscriptionOnboardingCompletionSummaryRow? get() = null

    val allowsBackNavigation: Boolean get() = true

    /** Whether this step should be shown. Skipped when false. */
    suspend fun shouldShow(): Boolean

    /** Creates the full-screen Fragment for this step. */
    fun createFragment(): Fragment
}

/**
 * How a step presents itself in the completion summary list [icon + label]
 */
data class SubscriptionOnboardingCompletionSummaryRow(
    @get:StringRes val labelResId: Int,
    @get:DrawableRes val pendingIconResId: Int,
)

/** How a step ended */
enum class SubscriptionOnboardingStepOutcome {
    COMPLETED,
    SKIPPED,
}

interface SubscriptionOnboardingController {
    /** Events for the host to react to. */
    val events: Flow<Event>

    /**
     * Call this when the current step is done, so the host can move on.
     *
     * @param stepId id of the step that finished.
     * @param outcome whether the step was completed, skipped, etc.
     * @param handoff optional action that sends the user to a feature right after onboarding ends.
     */
    fun onStepFinished(
        stepId: String,
        outcome: SubscriptionOnboardingStepOutcome,
        handoff: (() -> Unit)? = null,
    )

    /** Go back to the previous step. */
    fun onBack()

    /** Leave onboarding entirely. */
    fun exitOnboarding()

    sealed interface Event {
        data class StepFinished(
            val stepId: String,
            val outcome: SubscriptionOnboardingStepOutcome,
            val handoff: (() -> Unit)? = null,
        ) : Event
        data object Back : Event
        data object Exit : Event
    }
}
