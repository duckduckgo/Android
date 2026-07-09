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

package com.duckduckgo.subscriptions.impl.onboarding

import com.duckduckgo.onboarding.api.LinearOnboardingEvent

/**
 * Events fed into the subscription onboarding orchestrator. A step's Fragment reports completion through
 * [com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepHost]; the host translates that into
 * [StepFinished] for the current step, which advances the plan.
 */
sealed interface SubscriptionOnboardingEvent : LinearOnboardingEvent {
    data class StepFinished(val stepId: String) : SubscriptionOnboardingEvent
}
