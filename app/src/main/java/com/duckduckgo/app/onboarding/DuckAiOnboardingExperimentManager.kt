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
import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager.DuckAiOnboardingExperimentVariant.*
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles.DuckAiOnboardingExperimentCohort
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.isTablet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
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
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
    private val deviceInfo: DeviceInfo,
) : DuckAiOnboardingExperimentManager {

    override suspend fun enroll(): DuckAiOnboardingExperimentVariant? = withContext(dispatcherProvider.io()) {
        if (!arePrerequisitesMet()) return@withContext null

        val toggle = extendedOnboardingFeatureToggles.onboardingDuckAiExperimentMay26()

        if (toggle.isEnabled()) {
            toggle.enroll()
            toggle.getCohort()?.toVariant()
        } else {
            null
        }
    }

    private fun arePrerequisitesMet(): Boolean =
        browserConfig.showInputScreenOnboarding().isEnabled() &&
            browserConfig.singleTabFireDialog().isEnabled() &&
            !onboardingBrandDesignUpdateToggles.brandDesignUpdate().isEnabled() &&
            !deviceInfo.isTablet()

    private fun Toggle.State.Cohort.toVariant(): DuckAiOnboardingExperimentVariant? = when (name) {
        DuckAiOnboardingExperimentCohort.CONTROL.cohortName -> CONTROL
        DuckAiOnboardingExperimentCohort.TREATMENT_WITH_DUCK_AI_DEFAULT.cohortName -> TREATMENT_WITH_DUCK_AI_DEFAULT
        DuckAiOnboardingExperimentCohort.TREATMENT_WITH_SEARCH_DEFAULT.cohortName -> TREATMENT_WITH_SEARCH_DEFAULT
        else -> null
    }
}
