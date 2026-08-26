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

package com.duckduckgo.adblocking.impl.onboarding

import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.domain.SettingsPlacement
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin.Id
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Commits the onboarding ad blocking pick as the user's ad blocking setting, the same setting the
 * ad blocking settings screen writes.
 */
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = OnboardingBooleanPreferencePlugin::class,
    featureName = "pluginOnboardingAdBlockingPreferencePluginImpl",
    parentFeatureName = "pluginPointOnboardingBooleanPreference",
)
class OnboardingAdBlockingPreferencePluginImpl @Inject constructor(
    private val statusChecker: AdBlockingStatusChecker,
    private val settingsRepository: AdBlockingSettingsRepository,
) : OnboardingBooleanPreferencePlugin {

    override val id: Id = Id.AdBlocking

    /** Onboarding offers the preference exactly when the settings screen shows it. */
    override suspend fun isActive(): Boolean =
        statusChecker.settingsPlacementFlow().first() != SettingsPlacement.Hidden

    override suspend fun apply(enabled: Boolean) {
        settingsRepository.setEnabled(enabled)
    }
}
