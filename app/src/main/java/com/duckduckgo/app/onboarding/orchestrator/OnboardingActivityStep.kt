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

package com.duckduckgo.app.onboarding.orchestrator

import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingStep
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import com.duckduckgo.onboarding.api.LinearOnboardingTransition

data class OnboardingActivityStep(
    override val id: LinearOnboardingStepId,
    override val host: LinearOnboardingHost = LinearOnboardingHost.OnboardingActivity,
    override val precondition: suspend () -> Boolean = { true },
    override val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition,
    val resolveDialog: suspend () -> OnboardingActivityDialog,
) : LinearOnboardingStep
