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

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * A single step in the native subscription onboarding flow, contributed from the feature module that owns
 * the step (e.g. the VPN step lives in the VPN module, the Duck.ai step in the Duck.ai module). Each feature
 * module contributes one implementation as a multibinding; the host in `subscriptions-impl` collects them,
 * orders them (by `@PriorityKey`), and turns them into linear-onboarding steps.
 *
 * Keeping this contract framework-agnostic (just a [Fragment] factory + an id) is what lets a step live in
 * a different `-impl` module: the host never names the concrete Fragment class.
 */
interface SubscriptionOnboardingStepPlugin {
    /** Stable id for this step. Also the key used to persist completion. */
    val stepId: String

    /** Title shown in the host's toolbar while this step is on screen. */
    @get:StringRes
    val titleResId: Int

    /** Whether this step should be shown to the current user (e.g. gated by feature flag / entitlement). Skipped when false. */
    suspend fun shouldShow(): Boolean

    /** Creates the full-screen Fragment for this step. Dagger injection happens when it attaches to the host. */
    fun createFragment(): Fragment
}

/**
 * Implemented by the host activity (`SubscriptionOnboardingActivity`). A step's Fragment obtains it via
 * `requireActivity() as SubscriptionOnboardingStepHost` and calls back to drive the flow, so the Fragment
 * never depends on `subscriptions-impl` or the onboarding framework.
 */
interface SubscriptionOnboardingStepHost {
    /** The current step is done: advance to the next one, recording [outcome] for completion tracking. */
    fun onStepFinished(stepId: String, outcome: SubscriptionOnboardingStepOutcome)

    /** Leave the onboarding entirely (e.g. the Duck.ai step hands off to the chat). */
    fun exitOnboarding()
}

/** How a step ended. Only [COMPLETED] is persisted as a completed step. */
enum class SubscriptionOnboardingStepOutcome {
    COMPLETED,
    SKIPPED,
}

/**
 * Resolves the [SubscriptionOnboardingStepHost] for a step fragment without assuming it is hosted directly by
 * an Activity: prefers a parent fragment, then the hosting context. Errors if neither implements the contract.
 */
fun Fragment.requireSubscriptionOnboardingStepHost(): SubscriptionOnboardingStepHost =
    (parentFragment as? SubscriptionOnboardingStepHost)
        ?: (activity as? SubscriptionOnboardingStepHost)
        ?: error("Host must implement SubscriptionOnboardingStepHost")
