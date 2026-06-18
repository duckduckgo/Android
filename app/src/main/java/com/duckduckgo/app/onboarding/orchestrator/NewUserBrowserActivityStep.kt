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

/**
 * A [com.duckduckgo.app.browser.BrowserActivity]-hosted steps for [NewUserOnboardingPlanProvider].
 * The step describes WHAT BrowserActivity should do via [resolveAction];
 * HOW it is performed lives in [NewUserBrowserOnboardingViewModel].
 */
data class NewUserBrowserActivityStep(
    override val id: LinearOnboardingStepId,
    override val host: LinearOnboardingHost = LinearOnboardingHost.BrowserActivity,
    override val precondition: suspend () -> Boolean = { true },
    override val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition,
    val resolveAction: suspend () -> NewUserBrowserActivityAction,
) : LinearOnboardingStep

/** What the orchestrator asks BrowserActivity to do for a [NewUserBrowserActivityStep]. */
sealed interface NewUserBrowserActivityAction {
    /** Run the Duck.ai onboarding demo (arm it + open Duck.ai with [prompt]). */
    data class RunDuckAiOnboardingDemo(val prompt: String) : NewUserBrowserActivityAction
}
