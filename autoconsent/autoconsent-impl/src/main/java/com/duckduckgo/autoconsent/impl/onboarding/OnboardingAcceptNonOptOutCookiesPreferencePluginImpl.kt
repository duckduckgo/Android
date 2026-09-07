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

package com.duckduckgo.autoconsent.impl.onboarding

import android.content.Context
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.impl.R
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin.Id
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin.Preference
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = OnboardingBooleanPreferencePlugin::class,
    featureName = "pluginOnboardingAcceptNonOptOutCookiesPreferencePluginImpl",
    parentFeatureName = "pluginPointOnboardingBooleanPreference",
)
class OnboardingAcceptNonOptOutCookiesPreferencePluginImpl @Inject constructor(
    private val context: Context,
    private val autoconsent: Autoconsent,
    private val autoconsentFeature: AutoconsentFeature,
) : OnboardingBooleanPreferencePlugin {

    override val id: Id = Id.AcceptNonOptOutCookies

    /**
     * Settings only exposes this switch inside the cookie pop-up preference section, so offering it during
     * onboarding while that section is off would let the user set something they can never see again.
     */
    override suspend fun getPreference(): Preference? {
        if (!autoconsentFeature.self().isEnabled() || !autoconsentFeature.cookiePopUpPreferenceSetting().isEnabled()) return null

        return Preference(
            primaryText = context.getString(R.string.autoconsent_onboarding_accept_non_opt_out_cookies_primary),
            secondaryText = context.getString(R.string.autoconsent_onboarding_accept_non_opt_out_cookies_secondary),
            iconRes = CommonR.drawable.cookie_color_24,
        )
    }

    override suspend fun apply(enabled: Boolean) = autoconsent.changeClickAcceptEnabled(enabled)
}
