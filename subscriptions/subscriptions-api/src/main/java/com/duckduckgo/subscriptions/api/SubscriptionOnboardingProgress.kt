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

/**
 * Read-only access to the user's total (cross-session) onboarding progress. Intended for the
 * summary screen plugin, which can live in any module.
 *
 * Only [SubscriptionOnboardingStepType.STEP] screens count. The total is derived from the number of
 * registered step plugins.
 */
interface SubscriptionOnboardingProgress {

    /** Number of distinct steps the user has completed (across all sessions). */
    suspend fun completedStepCount(): Int

    /** Total number of real steps in the flow (registered STEP plugins). */
    suspend fun totalStepCount(): Int

    /** Completion as a 0..100 percentage. Returns 0 when there are no steps. */
    suspend fun completionPercent(): Int
}
