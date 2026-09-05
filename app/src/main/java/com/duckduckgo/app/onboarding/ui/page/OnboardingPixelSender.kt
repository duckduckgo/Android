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

package com.duckduckgo.app.onboarding.ui.page

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.orchestrator.PasswordImportOutcome
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.configdriven.DownloadReasonSelection
import com.duckduckgo.app.pixels.OnboardingPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * What happened in an onboarding step. Models the interaction, not the screen — the screen is the
 * [OnboardingPixelName] passed alongside it to [OnboardingPixelSender.fire]. Variants carry a typed payload only
 * where the pixel's `value` is screen-specific; everything else is a plain [Shown] or [Clicked].
 */
sealed interface OnboardingPixelAction {
    data object Shown : OnboardingPixelAction

    data class Clicked(val engaged: Boolean) : OnboardingPixelAction
    data class SetDefaultConfirmed(val isDdgDefault: Boolean) : OnboardingPixelAction
    data class WidgetConfirmed(val added: Boolean) : OnboardingPixelAction
    data class NotificationsConfirmed(val granted: Boolean) : OnboardingPixelAction
    data class AddressBarClicked(val position: OmnibarType) : OnboardingPixelAction
    data class SearchExperienceClicked(val withAi: Boolean) : OnboardingPixelAction
    data class TryInputClicked(
        val fromSuggestion: Boolean,
        val isChat: Boolean,
    ) : OnboardingPixelAction

    data class SuggestionClicked(val fromSuggestion: Boolean) : OnboardingPixelAction

    data class QuickSetupClicked(
        val addressBarPosition: OmnibarType,
        val inputScreenSelected: Boolean,
    ) : OnboardingPixelAction

    data class PasswordImportConfirmed(val outcome: PasswordImportOutcome) : OnboardingPixelAction

    data class DownloadReasonClicked(val reason: DownloadReasonSelection) : OnboardingPixelAction

    data class PreferencesClicked(val selections: Map<OnboardingPreference, Boolean>) : OnboardingPixelAction

    data class SingleChoiceClicked(val optionId: String) : OnboardingPixelAction
}

interface OnboardingPixelSender {
    fun fire(pixelName: OnboardingPixelName, action: OnboardingPixelAction)

    /**
     * Like [fire], but for contextual onboarding steps: their CTA can only ever be shown once and
     * resolved once (dismissed or confirmed) — never re-shown, unlike linear onboarding steps. The
     * dedup tag omits the value, so whichever value fires first for a given pixel+event is
     * authoritative and any later duplicate (e.g. from a stray navigation callback on a stale CTA
     * reference) is silently absorbed instead of double-counted.
     */
    fun fireContextual(pixelName: OnboardingPixelName, action: OnboardingPixelAction)

    /**
     * Records that the user went down the search branch of onboarding. Persisted so it can be
     * attached as the `variant` param to every subsequent onboarding pixel.
     */
    fun searchBranchSelected()

    /**
     * Records that the user went down the chat (Duck.ai) branch of onboarding. Persisted so it can be
     * attached as the `variant` param to every subsequent onboarding pixel.
     * */
    fun chatBranchSelected()

    /**
     * Records that this run is the download-reason segmented flow, so every pixel it fires carries
     * `flow=tailored_by_download_reason` instead of the default.
     */
    fun segmentedFlowStarted()

    /**
     * Clears the persisted flow and variant attribution. Called when a new linear onboarding run
     * starts: a run restarted after an app kill replays from before the branching step, so
     * attribution persisted by a previous run must not label this run's pre-branch pixels.
     */
    fun clearFlowAttribution()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealOnboardingPixelSender @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val appInstallStore: AppInstallStore,
    private val customAiOnboardingStore: CustomAiOnboardingStore,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val deviceInfo: DeviceInfo,
    private val appBuildConfig: AppBuildConfig,
    private val onboardingStore: OnboardingStore,
) : OnboardingPixelSender {

    private val variantPrefs by lazy { sharedPreferencesProvider.getSharedPreferences(PREFS_VARIANT_FILENAME) }

    private val isReinstallUser: Deferred<Boolean> by lazy {
        appCoroutineScope.async(dispatchers.io()) { appBuildConfig.isAppReinstall() }
    }

    override fun searchBranchSelected() {
        variantPrefs.edit().putString(PREFS_KEY_VARIANT, PREFS_VARIANT_SEARCH).apply()
    }

    override fun chatBranchSelected() {
        variantPrefs.edit().putString(PREFS_KEY_VARIANT, PREFS_VARIANT_CHAT).apply()
    }

    override fun segmentedFlowStarted() {
        variantPrefs.edit().putBoolean(PREFS_KEY_SEGMENTED_FLOW, true).apply()
    }

    override fun clearFlowAttribution() {
        variantPrefs.edit()
            .remove(PREFS_KEY_VARIANT)
            .remove(PREFS_KEY_SEGMENTED_FLOW)
            .apply()
    }

    override fun fire(pixelName: OnboardingPixelName, action: OnboardingPixelAction) {
        when (action) {
            OnboardingPixelAction.Shown ->
                fireStep(pixelName, PIXEL_EVENT_SHOWN)

            is OnboardingPixelAction.Clicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, engageOrDismiss(action.engaged))

            is OnboardingPixelAction.SetDefaultConfirmed ->
                fireStep(pixelName, PIXEL_EVENT_CONFIRMED, if (action.isDdgDefault) VALUE_DDG else VALUE_OTHER)

            is OnboardingPixelAction.WidgetConfirmed ->
                fireStep(pixelName, PIXEL_EVENT_CONFIRMED, if (action.added) VALUE_ADDED else VALUE_NOT_ADDED)

            is OnboardingPixelAction.NotificationsConfirmed ->
                fireStep(pixelName, PIXEL_EVENT_CONFIRMED, if (action.granted) VALUE_GRANTED else VALUE_DENIED)

            is OnboardingPixelAction.AddressBarClicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, addressBarValue(action.position))

            is OnboardingPixelAction.SearchExperienceClicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, if (action.withAi) SEARCH_PLUS_DUCKAI else SEARCH_ONLY)

            is OnboardingPixelAction.TryInputClicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, tryInputValue(action.fromSuggestion, action.isChat))

            is OnboardingPixelAction.SuggestionClicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, if (action.fromSuggestion) VALUE_SUGGESTED else VALUE_CUSTOM)

            is OnboardingPixelAction.QuickSetupClicked ->
                fireQuickSetupClicked(pixelName, action.addressBarPosition, action.inputScreenSelected)

            is OnboardingPixelAction.PasswordImportConfirmed ->
                fireStep(pixelName, PIXEL_EVENT_CONFIRMED, action.outcome.value)

            is OnboardingPixelAction.DownloadReasonClicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, action.reason.pixelToken)

            is OnboardingPixelAction.PreferencesClicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, extraParams = preferenceParams(action.selections))

            is OnboardingPixelAction.SingleChoiceClicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, action.optionId)
        }
    }

    override fun fireContextual(pixelName: OnboardingPixelName, action: OnboardingPixelAction) {
        when (action) {
            OnboardingPixelAction.Shown ->
                fireStep(pixelName, PIXEL_EVENT_SHOWN, includeValueInTag = false)

            is OnboardingPixelAction.Clicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, engageOrDismiss(action.engaged), includeValueInTag = false)

            is OnboardingPixelAction.SuggestionClicked ->
                fireStep(pixelName, PIXEL_EVENT_CLICKED, if (action.fromSuggestion) VALUE_SUGGESTED else VALUE_CUSTOM, includeValueInTag = false)

            else -> error("Unsupported contextual onboarding action: $action")
        }
    }

    private fun fireQuickSetupClicked(
        pixelName: OnboardingPixelName,
        addressBarPosition: OmnibarType,
        inputScreenSelected: Boolean,
    ) {
        appCoroutineScope.launch {
            val (isDefault, hasWidget) = withContext(dispatchers.io()) {
                defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
            }
            val inputType = if (inputScreenSelected) INPUT_TYPE_SEARCH_AND_DUCKAI else INPUT_TYPE_SEARCH
            val value = "$PIXEL_SET_AS_DEFAULT_VALUE_PARAM:${onOff(isDefault)}," +
                "$PIXEL_WIDGET_VALUE_PARAM:${onOff(hasWidget)}," +
                "$PIXEL_ADDRESS_BAR_VALUE_PARAM:${addressBarValue(addressBarPosition)}," +
                "$PIXEL_INPUT_TYPE_VALUE_PARAM:$inputType"
            val params = buildStandardParams().toMutableMap()
            params[PIXEL_PARAM_EVENT] = PIXEL_EVENT_CLICKED
            params[PIXEL_PARAM_VALUE] = value
            pixel.fire(
                pixel = pixelName,
                parameters = params,
                type = Unique(tag = "${pixelName.pixelName}_clicked_$value"),
            )
        }
    }

    private fun fireStep(
        pixelName: OnboardingPixelName,
        event: String,
        value: String? = null,
        includeValueInTag: Boolean = true,
        extraParams: Map<String, String> = emptyMap(),
    ) {
        appCoroutineScope.launch {
            val params = buildStandardParams().toMutableMap()
            params[PIXEL_PARAM_EVENT] = event
            value?.let { params[PIXEL_PARAM_VALUE] = it }
            params.putAll(extraParams)
            val tag = buildString {
                append(pixelName.pixelName).append("_").append(event)
                if (includeValueInTag) {
                    value?.let { append("_").append(it) }
                }
            }
            pixel.fire(pixel = pixelName, parameters = params, type = Unique(tag = tag))
        }
    }

    private suspend fun buildStandardParams(): Map<String, String> {
        // source/flow are install-level facts: CustomAiOnboardingStore is the canonical source (a
        // side-effect-free read of the decision persisted at plan build time).
        val reinstall = isReinstallUser.await()
        val (days, isCustomAiFlow, attribution) = withContext(dispatchers.io()) {
            Triple(
                appInstallStore.daysInstalled(),
                customAiOnboardingStore.isEnabled(),
                resolveFlowAttribution(),
            )
        }
        val params = mutableMapOf(
            PIXEL_PARAM_INSTALL_TYPE to if (reinstall) INSTALL_TYPE_REINSTALL else INSTALL_TYPE_NEW,
            // PIXEL_PARAM_SOURCE to null, - this will be added in a follow-up PR
            PIXEL_PARAM_FLOW to when {
                attribution.isSegmentedFlow -> FLOW_TAILORED_BY_DOWNLOAD_REASON
                isCustomAiFlow -> FLOW_DUCKAI
                else -> ONBOARDING_DEFAULT
            },
            PIXEL_PARAM_PIXEL_SOURCE to deviceInfo.formFactor().description,
        )
        attribution.branchVariant?.let { params[PIXEL_PARAM_VARIANT] = it }
        attribution.downloadReasonVariant?.let { params[PIXEL_PARAM_VARIANT_DOWNLOAD_REASON] = it }
        params[PIXEL_PARAM_DAYS_SINCE_INSTALL] = daysSinceInstallBucket(days)
        return params
    }

    private fun daysSinceInstallBucket(days: Long): String = when {
        days <= 0L -> DAYS_SINCE_INSTALL_0
        days <= 3L -> DAYS_SINCE_INSTALL_1_3
        days <= 10L -> DAYS_SINCE_INSTALL_4_10
        days <= 28L -> DAYS_SINCE_INSTALL_11_28
        else -> DAYS_SINCE_INSTALL_OVER_28
    }

    private fun resolveFlowAttribution() = FlowAttribution(
        isSegmentedFlow = variantPrefs.getBoolean(PREFS_KEY_SEGMENTED_FLOW, false),
        branchVariant = when (variantPrefs.getString(PREFS_KEY_VARIANT, null)) {
            PREFS_VARIANT_SEARCH -> VARIANT_SEARCH
            PREFS_VARIANT_CHAT -> VARIANT_CHAT
            else -> null
        },
        downloadReasonVariant = onboardingStore.getDownloadReason()
            ?.let { "$VARIANT_DOWNLOAD_REASON_PREFIX${it.pixelToken}" },
    )

    /**
     * The download reason and the search/chat branch are independent choices a segmented run can make
     * both of, so each gets its own param: a single one would have to encode the pairs, and its enum
     * would be the cross-product of the two.
     */
    private data class FlowAttribution(
        val isSegmentedFlow: Boolean,
        val branchVariant: String?,
        val downloadReasonVariant: String?,
    )

    private fun preferenceParams(selections: Map<OnboardingPreference, Boolean>): Map<String, String> =
        selections.entries.associate { (preference, enabled) ->
            // Duck.ai's row is worded as hiding AI images, but the param reports whether they're shown
            val reported = if (preference == OnboardingPreference.HIDE_AI_GENERATED_IMAGES) !enabled else enabled
            preference.pixelParamKey to reported.toString()
        }

    private val OnboardingPreference.pixelParamKey: String
        get() = when (this) {
            OnboardingPreference.SEARCH_HISTORY -> PARAM_RECENTLY_VISITED_SITES_ENABLED
            OnboardingPreference.SAFE_SEARCH -> PARAM_SAFE_SEARCH_ENABLED
            OnboardingPreference.SEARCH_ASSIST -> PARAM_SEARCH_ASSIST_ENABLED
            OnboardingPreference.HIDE_AI_GENERATED_IMAGES -> PARAM_AI_GENERATED_IMAGES_ENABLED
            OnboardingPreference.BLOCK_ADS -> PARAM_YOUTUBE_AD_BLOCKING_ENABLED
            OnboardingPreference.REJECT_OPTIONAL_COOKIES -> PARAM_COOKIE_POPUP_PROTECTION_ENABLED
            OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES -> PARAM_POPUPS_WITHOUT_OPTOUTS_ENABLED
        }

    private fun engageOrDismiss(engaged: Boolean): String = if (engaged) VALUE_ENGAGE else VALUE_DISMISS

    private fun tryInputValue(fromSuggestion: Boolean, isChat: Boolean): String {
        val source = if (fromSuggestion) VALUE_SUGGESTED else VALUE_CUSTOM
        val mode = if (isChat) VALUE_CHAT else VALUE_SEARCH
        return "${source}_$mode"
    }

    private fun addressBarValue(position: OmnibarType): String = when (position) {
        OmnibarType.SINGLE_TOP -> ADDRESS_BAR_TOP
        OmnibarType.SINGLE_BOTTOM -> ADDRESS_BAR_BOTTOM
        OmnibarType.SPLIT -> ADDRESS_BAR_SPLIT
    }

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private companion object {
        private const val PIXEL_PARAM_EVENT = "event"
        private const val PIXEL_PARAM_VALUE = "value"
        private const val PIXEL_PARAM_INSTALL_TYPE = "installType"
        private const val PIXEL_PARAM_DAYS_SINCE_INSTALL = "daysSinceInstall"
        private const val PIXEL_PARAM_FLOW = "flow"
        private const val PIXEL_PARAM_VARIANT = "variant"
        private const val PIXEL_PARAM_VARIANT_DOWNLOAD_REASON = "variant_download_reason"
        private const val PIXEL_PARAM_PIXEL_SOURCE = "pixelSource"

        private const val PIXEL_EVENT_SHOWN = "shown"
        private const val PIXEL_EVENT_CLICKED = "clicked"
        private const val PIXEL_EVENT_CONFIRMED = "confirmed"

        private const val INSTALL_TYPE_NEW = "new"
        private const val INSTALL_TYPE_REINSTALL = "reinstall"

        private const val ONBOARDING_DEFAULT = "default"
        private const val FLOW_DUCKAI = "duckai"
        private const val FLOW_TAILORED_BY_DOWNLOAD_REASON = "tailored_by_download_reason"

        private const val VARIANT_SEARCH = "search_plus_duckai-search"
        private const val VARIANT_CHAT = "search_plus_duckai-chat"
        private const val VARIANT_DOWNLOAD_REASON_PREFIX = "download_reason_"

        private const val PREFS_VARIANT_FILENAME = "com.duckduckgo.app.onboarding.variant"
        private const val PREFS_KEY_VARIANT = "variant"
        private const val PREFS_KEY_SEGMENTED_FLOW = "segmentedFlow"
        private const val PREFS_VARIANT_SEARCH = "search"
        private const val PREFS_VARIANT_CHAT = "chat"

        private const val PARAM_RECENTLY_VISITED_SITES_ENABLED = "recently_visited_sites_enabled"
        private const val PARAM_SAFE_SEARCH_ENABLED = "safe_search_enabled"
        private const val PARAM_SEARCH_ASSIST_ENABLED = "search_assist_enabled"
        private const val PARAM_AI_GENERATED_IMAGES_ENABLED = "ai_generated_images_enabled"
        private const val PARAM_YOUTUBE_AD_BLOCKING_ENABLED = "youtube_ad_blocking_enabled"
        private const val PARAM_COOKIE_POPUP_PROTECTION_ENABLED = "cookie_popup_protection_enabled"
        private const val PARAM_POPUPS_WITHOUT_OPTOUTS_ENABLED = "popups_without_optouts_enabled"

        private const val VALUE_ENGAGE = "engage"
        private const val VALUE_DISMISS = "dismiss"
        private const val VALUE_DDG = "ddg"
        private const val VALUE_OTHER = "other"
        private const val VALUE_ADDED = "added"
        private const val VALUE_NOT_ADDED = "not_added"
        private const val VALUE_GRANTED = "granted"
        private const val VALUE_DENIED = "denied"
        private const val SEARCH_ONLY = "search_only"
        private const val SEARCH_PLUS_DUCKAI = "search_plus_duckai"

        // Try-a-search composite-value components: {suggested|custom}_{search|chat}.
        private const val VALUE_SUGGESTED = "suggested"
        private const val VALUE_CUSTOM = "custom"
        private const val VALUE_SEARCH = "search"
        private const val VALUE_CHAT = "chat"

        private const val ADDRESS_BAR_TOP = "top"
        private const val ADDRESS_BAR_BOTTOM = "bottom"
        private const val ADDRESS_BAR_SPLIT = "split"

        // Quick-setup composite-value keys (preserved from QuickSetupPixelSender).
        private const val PIXEL_SET_AS_DEFAULT_VALUE_PARAM = "set_as_default"
        private const val PIXEL_WIDGET_VALUE_PARAM = "widget"
        private const val PIXEL_ADDRESS_BAR_VALUE_PARAM = "address_bar"
        private const val PIXEL_INPUT_TYPE_VALUE_PARAM = "input_type"
        private const val INPUT_TYPE_SEARCH = "search"
        private const val INPUT_TYPE_SEARCH_AND_DUCKAI = "search_and_duckai"

        private const val DAYS_SINCE_INSTALL_0 = "0"
        private const val DAYS_SINCE_INSTALL_1_3 = "1-3"
        private const val DAYS_SINCE_INSTALL_4_10 = "4-10"
        private const val DAYS_SINCE_INSTALL_11_28 = "11-28"
        private const val DAYS_SINCE_INSTALL_OVER_28 = "28+"
    }
}
