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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.TextConfig
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
import com.duckduckgo.mobile.android.R as CommonR

interface OnboardingPreferenceCatalog {

    /**
     * Rows for the [preferences] worth offering, in the order given. A preference that isn't available is
     * dropped rather than rendered, so an empty result means the step has nothing to show.
     */
    suspend fun offer(preferences: List<OnboardingPreference>): List<ContentConfig.PreferenceSelector.Row>

    suspend fun apply(selections: Map<OnboardingPreference, Boolean>)
}

@ContributesBinding(AppScope::class, boundType = OnboardingPreferenceCatalog::class)
@SingleInstanceIn(AppScope::class)
class OnboardingPreferenceCatalogImpl @Inject constructor(
    private val navigationHistory: NavigationHistory,
    private val serpSettingsDataProvider: SerpSettingsDataProvider,
    private val serpSettingsFeature: SerpSettingsFeature,
    private val booleanPreferencePlugins: ActivePluginPoint<OnboardingBooleanPreferencePlugin>,
    private val dispatcherProvider: DispatcherProvider,
) : OnboardingPreferenceCatalog {

    override suspend fun offer(preferences: List<OnboardingPreference>): List<ContentConfig.PreferenceSelector.Row> =
        withContext(dispatcherProvider.io()) {
            val rows = preferences.mapNotNull { definitions.getValue(it).row(it) }

            // Only offer a preference if it has no dependency or its dependency is also offered.
            val offered = rows.map { it.preference }.toSet()
            rows.filter { it.dependsOn == null || it.dependsOn in offered }
        }

    override suspend fun apply(selections: Map<OnboardingPreference, Boolean>): Unit = withContext(dispatcherProvider.io()) {
        selections.forEach { (preference, enabled) -> definitions.getValue(preference).apply(enabled) }
    }

    /**
     * A definition only holds lambdas, and the step that offers a preference is what evaluates them
     * to make sure that all the preconditions are checked just-in-time and allow maximal window for potential remote config flags loads, etc.
     */
    private val definitions: Map<OnboardingPreference, Definition> by lazy {
        val serpAvailable: suspend () -> Boolean = { serpSettingsFeature.storeSerpSettings().isEnabled() }

        mapOf(
            OnboardingPreference.SEARCH_HISTORY to definition(
                iconRes = CommonR.drawable.history_color_24,
                primary = R.string.searchPathPreferenceHistoryPrimary,
                secondary = R.string.searchPathPreferenceHistorySecondary,
                available = navigationHistory::isHistoryFeatureAvailable,
                seed = navigationHistory::isHistoryUserEnabled,
                apply = navigationHistory::setHistoryUserEnabled,
            ),

            OnboardingPreference.SAFE_SEARCH to definition(
                iconRes = CommonR.drawable.exclamation_color_24,
                primary = R.string.searchPathPreferenceSafePrimary,
                secondary = R.string.searchPathPreferenceSafeSecondary,
                available = serpAvailable,
                seed = ::safeSearchEnabled,
                apply = { enabled -> serpSettingsDataProvider.setSetting(if (enabled) SafeSearch.ON else SafeSearch.OFF) },
            ),

            OnboardingPreference.SEARCH_ASSIST to definition(
                iconRes = CommonR.drawable.search_assist_color_24,
                primary = R.string.noAiPathPreferenceSearchAssistPrimary,
                secondary = R.string.noAiPathPreferenceSearchAssistSecondary,
                available = serpAvailable,
                seed = { false },
                apply = { enabled ->
                    serpSettingsDataProvider.setSetting(
                        if (enabled) SearchAssistVisibility.SOMETIMES else SearchAssistVisibility.NEVER,
                    )
                },
            ),

            OnboardingPreference.HIDE_AI_GENERATED_IMAGES to definition(
                iconRes = CommonR.drawable.ai_images_strikethrough_color_24,
                primary = R.string.noAiPathPreferenceHideAiGeneratedImagesPrimary,
                secondary = R.string.noAiPathPreferenceHideAiGeneratedImagesSecondary,
                available = serpAvailable,
                seed = { true },
                apply = { enabled ->
                    serpSettingsDataProvider.setSetting(
                        if (enabled) HideAiGeneratedImages.ON else HideAiGeneratedImages.OFF,
                    )
                },
            ),

            OnboardingPreference.BLOCK_ADS to fromPlugin(
                id = OnboardingBooleanPreferencePlugin.Id.AdBlocking,
                seed = { true },
            ),

            OnboardingPreference.REJECT_OPTIONAL_COOKIES to fromPlugin(
                id = OnboardingBooleanPreferencePlugin.Id.RejectOptionalCookies,
                seed = { true },
            ),

            OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES to fromPlugin(
                id = OnboardingBooleanPreferencePlugin.Id.AcceptNonOptOutCookies,
                dependsOn = OnboardingPreference.REJECT_OPTIONAL_COOKIES,
                seed = { false },
            ),
        ).also { definitions ->
            check(definitions.keys == OnboardingPreference.entries.toSet()) {
                "Every onboarding preference needs a definition"
            }
        }
    }

    private class Definition(
        /** Null when the preference can't be offered. */
        val row: suspend (OnboardingPreference) -> ContentConfig.PreferenceSelector.Row?,
        val apply: suspend (Boolean) -> Unit,
    )

    private fun definition(
        @DrawableRes iconRes: Int,
        @StringRes primary: Int,
        @StringRes secondary: Int,
        available: suspend () -> Boolean,
        seed: suspend () -> Boolean,
        apply: suspend (Boolean) -> Unit,
    ) = Definition(
        row = { preference ->
            if (available()) {
                ContentConfig.PreferenceSelector.Row(
                    preference = preference,
                    iconRes = iconRes,
                    primaryText = TextConfig.Resource(primary),
                    secondaryText = TextConfig.Resource(secondary),
                    initiallyEnabled = seed(),
                )
            } else {
                null
            }
        },
        apply = apply,
    )

    /**
     * A preference whose owning module supplies the copy, the icon and the write process.
     * The plugin declining to describe the preference is what makes the row unavailable, so a module can
     * withdraw the preference behind its own conditions.
     */
    private fun fromPlugin(
        id: OnboardingBooleanPreferencePlugin.Id,
        dependsOn: OnboardingPreference? = null,
        seed: suspend () -> Boolean,
    ) = Definition(
        row = { preference ->
            plugin(id)?.getPreference()?.let {
                ContentConfig.PreferenceSelector.Row(
                    preference = preference,
                    iconRes = it.iconRes,
                    primaryText = TextConfig.Literal(it.primaryText),
                    secondaryText = it.secondaryText?.let(TextConfig::Literal),
                    initiallyEnabled = seed(),
                    dependsOn = dependsOn,
                )
            }
        },
        apply = { enabled -> plugin(id)?.apply(enabled) },
    )

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
