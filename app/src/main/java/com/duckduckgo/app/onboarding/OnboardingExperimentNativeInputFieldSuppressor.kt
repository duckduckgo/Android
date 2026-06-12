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

package com.duckduckgo.app.onboarding

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputFieldSuppressor
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Suppresses the native (unified) input field for users enrolled in the active Duck.ai onboarding
 * experiment so they keep the legacy omnibar while the experiment runs.
 *
 * Note: this value is only read when `RealDuckChat.cacheConfig()` runs (app init and on privacy
 * config download), so a user who enrolls mid-session would keep the unified input until the next
 * refresh or restart. We don't handle that case on purpose: enrollment for this experiment is
 * closed, so the enrolled population is already assigned before the app starts and never changes
 * within a session.
 */
@ContributesMultibinding(AppScope::class)
class OnboardingExperimentNativeInputFieldSuppressor @Inject constructor(
    private val duckAiOnboardingExperimentManager: DuckAiOnboardingExperimentManager,
) : NativeInputFieldSuppressor {
    override suspend fun isNativeInputFieldSuppressed(): Boolean =
        duckAiOnboardingExperimentManager.isEnrolledInActiveExperiment()
}
