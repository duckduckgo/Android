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

import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin
import com.duckduckgo.settings.api.HideAiGeneratedImages
import com.duckduckgo.settings.api.SafeSearch
import com.duckduckgo.settings.api.SearchAssistVisibility
import com.duckduckgo.settings.api.SerpSettingsDataProvider
import com.duckduckgo.settings.api.SerpSettingsFeature
import com.duckduckgo.settings.api.observeSetting
import com.duckduckgo.settings.api.setSetting
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The onboarding-facing view of the settings offered by the preference selector: each [OnboardingPreference]
 * hides where its value actually lives, so callers never deal with the SERP's raw keys or the history store.
 */
interface OnboardingPreferenceApplier {

    /** Whether the preference can be offered at all; a preference that isn't available is not shown as a row. */
    suspend fun isAvailable(preference: OnboardingPreference): Boolean

    /**
     * The value to seed the row's switch with. Usually the value the preference holds right now, but a path
     * that offers a preference to steer the user towards a position seeds that position instead.
     */
    suspend fun isEnabled(preference: OnboardingPreference): Boolean

    suspend fun apply(preference: OnboardingPreference, enabled: Boolean)
}

@ContributesBinding(AppScope::class, boundType = OnboardingPreferenceApplier::class)
@SingleInstanceIn(AppScope::class)
class OnboardingPreferenceApplierImpl @Inject constructor(
    private val navigationHistory: NavigationHistory,
    private val autoconsent: Autoconsent,
    private val serpSettingsDataProvider: SerpSettingsDataProvider,
    private val serpSettingsFeature: SerpSettingsFeature,
    private val booleanPreferencePlugins: ActivePluginPoint<OnboardingBooleanPreferencePlugin>,
    private val dispatcherProvider: DispatcherProvider,
) : OnboardingPreferenceApplier {

    override suspend fun isAvailable(preference: OnboardingPreference): Boolean = withContext(dispatcherProvider.io()) {
        when (preference) {
            OnboardingPreference.BLOCK_ADS -> plugin(OnboardingBooleanPreferencePlugin.Id.AdBlocking) != null
            OnboardingPreference.SEARCH_HISTORY -> navigationHistory.isHistoryFeatureAvailable()
            OnboardingPreference.SAFE_SEARCH,
            OnboardingPreference.SEARCH_ASSIST,
            OnboardingPreference.HIDE_AI_GENERATED_IMAGES,
            -> serpSettingsFeature.storeSerpSettings().isEnabled()
            OnboardingPreference.REJECT_OPTIONAL_COOKIES,
            OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES,
            -> true
        }
    }

    override suspend fun isEnabled(preference: OnboardingPreference): Boolean = withContext(dispatcherProvider.io()) {
        when (preference) {
            OnboardingPreference.SEARCH_HISTORY -> navigationHistory.isHistoryUserEnabled()
            OnboardingPreference.SAFE_SEARCH -> safeSearchEnabled()
            OnboardingPreference.BLOCK_ADS -> true
            OnboardingPreference.SEARCH_ASSIST -> false
            OnboardingPreference.HIDE_AI_GENERATED_IMAGES -> true
            OnboardingPreference.REJECT_OPTIONAL_COOKIES -> autoconsent.isSettingEnabled()
            OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES -> autoconsent.isClickAcceptEnabled()
        }
    }

    override suspend fun apply(preference: OnboardingPreference, enabled: Boolean): Unit = withContext(dispatcherProvider.io()) {
        when (preference) {
            OnboardingPreference.BLOCK_ADS -> plugin(OnboardingBooleanPreferencePlugin.Id.AdBlocking)?.apply(enabled)
            OnboardingPreference.SEARCH_HISTORY -> navigationHistory.setHistoryUserEnabled(enabled)
            OnboardingPreference.SAFE_SEARCH -> serpSettingsDataProvider.setSetting(if (enabled) SafeSearch.ON else SafeSearch.OFF)
            OnboardingPreference.SEARCH_ASSIST -> serpSettingsDataProvider.setSetting(
                if (enabled) SearchAssistVisibility.SOMETIMES else SearchAssistVisibility.NEVER,
            )
            OnboardingPreference.HIDE_AI_GENERATED_IMAGES -> serpSettingsDataProvider.setSetting(
                if (enabled) HideAiGeneratedImages.ON else HideAiGeneratedImages.OFF,
            )
            OnboardingPreference.REJECT_OPTIONAL_COOKIES -> autoconsent.changeSetting(enabled)
            OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES -> autoconsent.changeClickAcceptEnabled(enabled)
        }
    }

    private suspend fun plugin(id: OnboardingBooleanPreferencePlugin.Id): OnboardingBooleanPreferencePlugin? =
        booleanPreferencePlugins.getPlugins().firstOrNull { it.id == id }

    /**
     * The SERP only pushes `kp` to us once the user has loaded a SERP, so during onboarding it is normally absent:
     * we fall back to DuckDuckGo's own default of safe search being on. The timeout guards the step against a
     * settings flow that never emits, which would otherwise stall the screen behind a blank card.
     */
    private suspend fun safeSearchEnabled(): Boolean {
        val stored = withTimeoutOrNull(SERP_SETTING_READ_TIMEOUT) {
            serpSettingsDataProvider.observeSetting(SafeSearch.ON).firstOrNull()
        }
        return stored != SafeSearch.OFF
    }

    private companion object {
        val SERP_SETTING_READ_TIMEOUT = 500.milliseconds
    }
}
