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

import com.duckduckgo.app.onboarding.SegmentedOnboardingExperimentManager.SegmentedOnboardingExperimentVariant
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SegmentedOnboardingExperimentManager {
    suspend fun enroll(): SegmentedOnboardingExperimentVariant?

    enum class SegmentedOnboardingExperimentVariant {
        CONTROL,
        TREATMENT,
    }
}

@ContributesBinding(AppScope::class, boundType = SegmentedOnboardingExperimentManager::class)
@SingleInstanceIn(AppScope::class)
class SegmentedOnboardingExperimentManagerImpl @Inject constructor(
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
    private val dispatcherProvider: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val onboardingPrivacyConfigPersistedGate: OnboardingPrivacyConfigPersistedGate,
) : SegmentedOnboardingExperimentManager {

    override suspend fun enroll(): SegmentedOnboardingExperimentVariant? = withContext(dispatcherProvider.io()) {
        if (onboardingPrivacyConfigPersistedGate.awaitPersisted() && checkPrerequisites()) {
            // scaffolding for future experiment enrollment logic
            return@withContext null
        } else {
            null
        }
    }

    private suspend fun checkPrerequisites() =
        onboardingBrandDesignUpdateToggles.configDrivenDialogs().isEnabled() &&
            !appBuildConfig.isAppReinstall()
}
