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

import com.duckduckgo.app.onboarding.OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface OnboardingPromptsExperimentManager {

    suspend fun enroll(): OnboardingPromptExperimentVariant?

    enum class OnboardingPromptExperimentVariant {
        CONTROL,
        TREATMENT_DOCK_ONLY,
        TREATMENT_WIDGET_ONLY,
        TREATMENT_DOCK_AND_WIDGET,
    }
}

@ContributesBinding(AppScope::class, boundType = OnboardingPromptsExperimentManager::class)
@SingleInstanceIn(AppScope::class)
class OnboardingPromptsExperimentManagerImpl @Inject constructor(
    private val toggles: OnboardingPromptsToggles,
    private val dispatcherProvider: DispatcherProvider,
    private val onboardingPrivacyConfigPersistedGate: OnboardingPrivacyConfigPersistedGate,
) : OnboardingPromptsExperimentManager {

    override suspend fun enroll(): OnboardingPromptExperimentVariant? = withContext(dispatcherProvider.io()) {
        if (onboardingPrivacyConfigPersistedGate.awaitPersisted()) {
            val toggle = toggles.addToDockAndWidgetExperimentJul25()
            toggle.enroll()
            when {
                toggle.isEnrolledAndEnabled(OnboardingPromptsToggles.OnboardingPromptsCohorts.TREATMENT_DOCK_AND_WIDGET) -> {
                    OnboardingPromptExperimentVariant.TREATMENT_DOCK_AND_WIDGET
                }

                toggle.isEnrolledAndEnabled(OnboardingPromptsToggles.OnboardingPromptsCohorts.TREATMENT_WIDGET_ONLY) -> {
                    OnboardingPromptExperimentVariant.TREATMENT_WIDGET_ONLY
                }

                toggle.isEnrolledAndEnabled(OnboardingPromptsToggles.OnboardingPromptsCohorts.TREATMENT_DOCK_ONLY) -> {
                    OnboardingPromptExperimentVariant.TREATMENT_DOCK_ONLY
                }

                toggle.isEnrolledAndEnabled(OnboardingPromptsToggles.OnboardingPromptsCohorts.CONTROL) -> {
                    OnboardingPromptExperimentVariant.CONTROL
                }

                else -> null
            }
        } else {
            null
        }
    }
}
