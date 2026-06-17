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

import com.duckduckgo.onboarding.api.LinearOnboardingResult

/**
 * Per-run cross-step state. A fresh instance is created inside
 * [NewUserOnboardingPlanProvider.buildRootPlan]. Holds only cross-step facts — editable
 * selections live in the VM.
 */
class NewUserOnboardingPlanContext {
    /** Raw [com.duckduckgo.appbuildconfig.api.AppBuildConfig.isAppReinstall] result, set by the first-dialog memo. */
    @Volatile
    var isReinstall: Boolean = false

    /**
     * Confirmed input-mode choice, written by the [NewUserOnboardingStepIds.INPUT_SCREEN] step,
     * read by the [NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW] precondition.
     */
    @Volatile
    var inputModeWasAi: Boolean = false

    /**
     * Outcome surfaced on [LinearOnboardingResult]-carrying [com.duckduckgo.onboarding.api.LinearOnboardingState.Completed].
     */
    @Volatile
    var completionResult: LinearOnboardingResult? = null
}
