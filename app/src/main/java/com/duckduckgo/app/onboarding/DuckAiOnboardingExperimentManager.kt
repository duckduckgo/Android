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

import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager.DuckAiOnboardingExperimentVariant
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface DuckAiOnboardingExperimentManager {

    /**
     * Attempts to enroll the user into the experiment and returns the assigned variant.
     * Returns null if the user was not enrolled into the experiment.
     */
    suspend fun enroll(): DuckAiOnboardingExperimentVariant?

    enum class DuckAiOnboardingExperimentVariant {
        CONTROL,
        TREATMENT_WITH_DUCK_AI_DEFAULT,
        TREATMENT_WITH_SEARCH_DEFAULT,
    }
}

@ContributesBinding(AppScope::class)
class DuckAiOnboardingExperimentManagerImpl @Inject constructor(
    private val browserConfig: AndroidBrowserConfigFeature,
    private val dispatcherProvider: DispatcherProvider,
) : DuckAiOnboardingExperimentManager {

    override suspend fun enroll(): DuckAiOnboardingExperimentVariant? {
        if (!arePrerequisitesMet()) return null

        // TODO experiment setup
        return DuckAiOnboardingExperimentVariant.TREATMENT_WITH_DUCK_AI_DEFAULT
    }

    private suspend fun arePrerequisitesMet(): Boolean = withContext(dispatcherProvider.io()) {
        browserConfig.showInputScreenOnboarding().isEnabled() &&
            browserConfig.singleTabFireDialog().isEnabled()
    }
}
