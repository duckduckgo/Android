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

import com.duckduckgo.app.onboarding.OnboardingPasswordImportExperimentManager.OnboardingPasswordImportVariant
import com.duckduckgo.app.onboarding.OnboardingPasswordImportToggles.OnboardingPasswordImportCohorts
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.ImportPasswordsFromGoogle
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface OnboardingPasswordImportExperimentManager {

    suspend fun enroll(): OnboardingPasswordImportVariant?

    enum class OnboardingPasswordImportVariant {
        CONTROL,
        TREATMENT,
    }
}

@ContributesBinding(AppScope::class, boundType = OnboardingPasswordImportExperimentManager::class)
@SingleInstanceIn(AppScope::class)
class OnboardingPasswordImportExperimentManagerImpl @Inject constructor(
    private val toggles: OnboardingPasswordImportToggles,
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
    private val importPasswordsFromGoogle: ImportPasswordsFromGoogle,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val onboardingPrivacyConfigPersistedGate: OnboardingPrivacyConfigPersistedGate,
) : OnboardingPasswordImportExperimentManager {

    override suspend fun enroll(): OnboardingPasswordImportVariant? = withContext(dispatcherProvider.io()) {
        if (onboardingPrivacyConfigPersistedGate.awaitPersisted() && checkPrerequisites()) {
            val toggle = toggles.passwordImportExperimentAug25()
            toggle.enroll()
            when {
                toggle.isEnrolledAndEnabled(OnboardingPasswordImportCohorts.TREATMENT) -> OnboardingPasswordImportVariant.TREATMENT
                toggle.isEnrolledAndEnabled(OnboardingPasswordImportCohorts.CONTROL) -> OnboardingPasswordImportVariant.CONTROL
                else -> null
            }
        } else {
            null
        }
    }

    /**
     * Checked before enrolling, so users who could never reach the step are kept out of the experiment: it exists
     * only in the config-driven onboarding, only for new installs, and only where the import flow is supported.
     */
    private suspend fun checkPrerequisites() =
        toggles.self().isEnabled() &&
            onboardingBrandDesignUpdateToggles.configDrivenDialogs().isEnabled() &&
            !appBuildConfig.isAppReinstall() &&
            importPasswordsFromGoogle.isSupported()
}
