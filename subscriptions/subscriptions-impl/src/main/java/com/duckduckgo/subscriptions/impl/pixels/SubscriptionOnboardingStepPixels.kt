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

package com.duckduckgo.subscriptions.impl.pixels

/**
 * Clickthrough-rate pixels for the native subscription onboarding steps, as defined by the onboarding
 * experiments. The wire name embeds the action and step name
 * (`m_subscription_onboarding_step_{shown|completed|skipped}_{stepName}`) because the experiment analysis
 * queries those exact names.
 *
 * [Step] maps the stable plugin `stepId` (which may be owned by another feature module, so it is matched by
 * string rather than by an import) to the step name used in the pixel. The completion step is impressions
 * only, so it has no completed/skipped variant.
 */
object SubscriptionOnboardingStepPixels {

    private const val PREFIX = "m_subscription_onboarding_step"

    enum class Action(val wireName: String) {
        SHOWN("shown"),
        COMPLETED("completed"),
        SKIPPED("skipped"),
    }

    enum class Step(val stepId: String, val wireName: String) {
        INTRO(stepId = "welcome", wireName = "intro"),
        FEATURES_SUMMARY(stepId = "features_summary", wireName = "features_summary"),
        VPN(stepId = "vpn", wireName = "vpn"),
        IDTR(stepId = "itr", wireName = "idtr"),
        DUCK_AI(stepId = "duck_ai", wireName = "duck_ai"),
        PIR(stepId = "pir", wireName = "pir"),
        COMPLETION(stepId = "completion", wireName = "completion"),
        ;

        /** The completion screen auto-advances, so only its impression is meaningful. */
        val supportsOutcome: Boolean get() = this != COMPLETION

        companion object {
            fun fromStepId(stepId: String): Step? = entries.firstOrNull { it.stepId == stepId }
        }
    }

    fun baseName(action: Action, step: Step): String = "${PREFIX}_${action.wireName}_${step.wireName}"

    /** Every base name that can be fired, for pixel param-removal registration. */
    fun allBaseNames(): List<String> = buildList {
        Step.entries.forEach { step ->
            add(baseName(Action.SHOWN, step))
            if (step.supportsOutcome) {
                add(baseName(Action.COMPLETED, step))
                add(baseName(Action.SKIPPED, step))
            }
        }
    }
}
