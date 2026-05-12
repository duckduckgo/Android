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

package com.duckduckgo.app.onboardingquicksetup

import com.duckduckgo.app.onboardingquicksetup.OnboardingQuickSetupExperimentManager.QuickSetupExperimentVariant
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface OnboardingQuickSetupExperimentManager {

    /**
     * Attempts to enroll the user into the quick-setup experiment and returns the assigned variant.
     * Returns null if the user was not enrolled into the experiment.
     */
    suspend fun enroll(): QuickSetupExperimentVariant?

    enum class QuickSetupExperimentVariant {
        CONTROL,
        TREATMENT,
    }
}

@ContributesBinding(AppScope::class)
class OnboardingQuickSetupExperimentManagerImpl @Inject constructor(
    private val toggles: OnboardingQuickSetupToggles,
    private val dispatcherProvider: DispatcherProvider,
) : OnboardingQuickSetupExperimentManager {

    override suspend fun enroll(): QuickSetupExperimentVariant? = withContext(dispatcherProvider.io()) {
        toggles.quickSetup().enroll()
        when {
            // toggles.quickSetup().isEnrolledAndEnabled(QuickSetupCohorts.TREATMENT) -> {
            //     QuickSetupExperimentVariant.TREATMENT
            // }
            //
            // toggles.quickSetup().isEnrolledAndEnabled(QuickSetupCohorts.CONTROL) -> {
            //     QuickSetupExperimentVariant.CONTROL
            // }

            toggles.quickSetup().isEnabled() -> {
                // If the experiment is enabled but the user is not enrolled in either cohort, we will enroll them in the treatment cohort by default.
                // This is temporary and will be changed in the future
                QuickSetupExperimentVariant.TREATMENT
            }

            else -> {
                null
            }
        }
    }
}
