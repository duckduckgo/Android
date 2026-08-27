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

import android.content.Context
import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.domain.AdBlockingState.Disabled
import com.duckduckgo.adblocking.impl.domain.AdBlockingState.Enabled
import com.duckduckgo.adblocking.impl.domain.AdBlockingState.Uninitialized
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.domain.SettingsPlacement
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin.Id
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = OnboardingBooleanPreferencePlugin::class,
    featureName = "pluginOnboardingAdBlockingPreferencePluginImpl",
    parentFeatureName = "pluginPointOnboardingBooleanPreference",
)
class OnboardingAdBlockingPreferencePluginImpl @Inject constructor(
    private val context: Context,
    private val statusChecker: AdBlockingStatusChecker,
    private val settingsRepository: AdBlockingSettingsRepository,
) : OnboardingBooleanPreferencePlugin {

    override val id: Id = Id.AdBlocking

    override val primaryText: String get() = context.getString(R.string.ad_blocking_onboarding_preference_primary)

    override val iconRes: Int = CommonR.drawable.ads_blocked_color_24

    override suspend fun isActive(): Boolean {
        val placement = statusChecker.settingsPlacementFlow().firstOrNull()
        return placement != null && placement != SettingsPlacement.Hidden
    }

    override suspend fun apply(enabled: Boolean) {
        // Persisting a value that already applies would turn the remote default into an explicit user
        // choice, so the setting would stop following a later change to that default.
        val alreadyApplies = when (statusChecker.observeState().firstOrNull()) {
            Enabled.UserEnabled, Enabled.Default -> enabled
            Disabled.Permanent -> !enabled
            // A session-scoped kill hides the persisted choice, and a state that never arrives leaves
            // nothing to compare against.
            Disabled.UntilRelaunch, Uninitialized, null -> false
        }
        if (!alreadyApplies) {
            settingsRepository.setEnabled(enabled)
        }
    }
}
