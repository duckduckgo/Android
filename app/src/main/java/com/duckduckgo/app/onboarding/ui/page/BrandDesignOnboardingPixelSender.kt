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
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface OnboardingPixelEvent {
    data object WelcomeShown : OnboardingPixelEvent
    data class WelcomeClicked(val engaged: Boolean) : OnboardingPixelEvent

    data object SetDefaultShown : OnboardingPixelEvent
    data object SetDefaultClicked : OnboardingPixelEvent
    data class SetDefaultConfirmed(val isDdgDefault: Boolean) : OnboardingPixelEvent

    data object AddressBarPositionShown : OnboardingPixelEvent
    data class AddressBarPositionClicked(val position: OmnibarType) : OnboardingPixelEvent

    data object SearchExperienceShown : OnboardingPixelEvent
    data class SearchExperienceClicked(val withAi: Boolean) : OnboardingPixelEvent

    data object SkipOnboardingShown : OnboardingPixelEvent
    data class SkipOnboardingClicked(val engaged: Boolean) : OnboardingPixelEvent

    data object NotificationsShown : OnboardingPixelEvent
    data class NotificationsConfirmed(val granted: Boolean) : OnboardingPixelEvent

    data object QuickSetupShown : OnboardingPixelEvent
    data class QuickSetupClicked(
        val addressBarPosition: OmnibarType,
        val inputScreenSelected: Boolean,
    ) : OnboardingPixelEvent

    data object SyncRestoreShown : OnboardingPixelEvent
    data class SyncRestoreClicked(val engaged: Boolean) : OnboardingPixelEvent

    data object TryASearchShown : OnboardingPixelEvent
    data class TryASearchClicked(
        val fromSuggestion: Boolean,
        val isChat: Boolean,
    ) : OnboardingPixelEvent

    data object AiComparisonShown : OnboardingPixelEvent
    data object AiComparisonClicked : OnboardingPixelEvent
}

sealed interface OnboardingAction {
    data object Shown : OnboardingAction
    data class PrimaryClick(
        val addressBarPosition: OmnibarType? = null,
        val withAi: Boolean? = null,
    ) : OnboardingAction

    data object SecondaryClick : OnboardingAction
}

fun PreOnboardingDialogType.toEvent(action: OnboardingAction): OnboardingPixelEvent? =
    when (this) {
        PreOnboardingDialogType.INITIAL, PreOnboardingDialogType.INITIAL_REINSTALL_USER -> when (action) {
            OnboardingAction.Shown -> OnboardingPixelEvent.WelcomeShown
            is OnboardingAction.PrimaryClick -> OnboardingPixelEvent.WelcomeClicked(engaged = true)
            OnboardingAction.SecondaryClick -> OnboardingPixelEvent.WelcomeClicked(engaged = false)
        }

        PreOnboardingDialogType.COMPARISON_CHART -> when (action) {
            OnboardingAction.Shown -> OnboardingPixelEvent.SetDefaultShown
            is OnboardingAction.PrimaryClick -> OnboardingPixelEvent.SetDefaultClicked
            OnboardingAction.SecondaryClick -> null
        }

        PreOnboardingDialogType.ADDRESS_BAR_POSITION -> when (action) {
            OnboardingAction.Shown -> OnboardingPixelEvent.AddressBarPositionShown
            is OnboardingAction.PrimaryClick -> OnboardingPixelEvent.AddressBarPositionClicked(
                action.addressBarPosition ?: OmnibarType.SINGLE_TOP,
            )

            OnboardingAction.SecondaryClick -> null
        }

        PreOnboardingDialogType.INPUT_SCREEN -> when (action) {
            OnboardingAction.Shown -> OnboardingPixelEvent.SearchExperienceShown
            is OnboardingAction.PrimaryClick -> OnboardingPixelEvent.SearchExperienceClicked(action.withAi ?: false)
            OnboardingAction.SecondaryClick -> null
        }

        PreOnboardingDialogType.SKIP_ONBOARDING_OPTION -> when (action) {
            OnboardingAction.Shown -> OnboardingPixelEvent.SkipOnboardingShown
            is OnboardingAction.PrimaryClick -> OnboardingPixelEvent.SkipOnboardingClicked(engaged = true)
            OnboardingAction.SecondaryClick -> OnboardingPixelEvent.SkipOnboardingClicked(engaged = false)
        }

        PreOnboardingDialogType.QUICK_SETUP -> when (action) {
            OnboardingAction.Shown -> OnboardingPixelEvent.QuickSetupShown
            // Quick-setup clicks are fired inline (they need async default-browser/widget lookups).
            is OnboardingAction.PrimaryClick, OnboardingAction.SecondaryClick -> null
        }

        PreOnboardingDialogType.SYNC_RESTORE -> when (action) {
            OnboardingAction.Shown -> OnboardingPixelEvent.SyncRestoreShown
            is OnboardingAction.PrimaryClick -> OnboardingPixelEvent.SyncRestoreClicked(engaged = true)
            OnboardingAction.SecondaryClick -> OnboardingPixelEvent.SyncRestoreClicked(engaged = false)
        }

        PreOnboardingDialogType.INPUT_SCREEN_PREVIEW -> when (action) {
            OnboardingAction.Shown -> OnboardingPixelEvent.TryASearchShown
            // The try-a-search click carries the tried query's source/mode, known only at submission time,
            // so it is fired inline from the demo-query callback rather than from a plain CTA tap here.
            is OnboardingAction.PrimaryClick, OnboardingAction.SecondaryClick -> null
        }

        PreOnboardingDialogType.AI_COMPARISON_CHART -> when (action) {
            OnboardingAction.Shown -> OnboardingPixelEvent.AiComparisonShown
            is OnboardingAction.PrimaryClick -> OnboardingPixelEvent.AiComparisonClicked
            OnboardingAction.SecondaryClick -> null
        }
    }

interface BrandDesignOnboardingPixelSender {
    fun fire(event: OnboardingPixelEvent)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrandDesignOnboardingPixelSender @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val appInstallStore: AppInstallStore,
    private val customAiOnboardingStore: CustomAiOnboardingStore,
    private val onboardingStore: OnboardingStore,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val deviceInfo: DeviceInfo,
    private val appBuildConfig: AppBuildConfig,
) : BrandDesignOnboardingPixelSender {

    private val isReinstallUser: Deferred<Boolean> by lazy {
        appCoroutineScope.async(dispatchers.io()) { appBuildConfig.isAppReinstall() }
    }

    override fun fire(event: OnboardingPixelEvent) {
        when (event) {
            OnboardingPixelEvent.WelcomeShown ->
                fireStep(AppPixelName.ONBOARDING_WELCOME, PIXEL_EVENT_SHOWN)

            is OnboardingPixelEvent.WelcomeClicked ->
                fireStep(AppPixelName.ONBOARDING_WELCOME, PIXEL_EVENT_CLICKED, engageOrDismiss(event.engaged))

            OnboardingPixelEvent.SetDefaultShown ->
                fireStep(AppPixelName.ONBOARDING_SET_DEFAULT, PIXEL_EVENT_SHOWN)

            OnboardingPixelEvent.SetDefaultClicked ->
                fireStep(AppPixelName.ONBOARDING_SET_DEFAULT, PIXEL_EVENT_CLICKED)

            is OnboardingPixelEvent.SetDefaultConfirmed ->
                fireStep(
                    AppPixelName.ONBOARDING_SET_DEFAULT,
                    PIXEL_EVENT_CONFIRMED,
                    if (event.isDdgDefault) VALUE_DDG else VALUE_OTHER,
                )

            OnboardingPixelEvent.AddressBarPositionShown ->
                fireStep(AppPixelName.ONBOARDING_ADDRESS_BAR_POSITION, PIXEL_EVENT_SHOWN)

            is OnboardingPixelEvent.AddressBarPositionClicked ->
                fireStep(AppPixelName.ONBOARDING_ADDRESS_BAR_POSITION, PIXEL_EVENT_CLICKED, addressBarValue(event.position))

            OnboardingPixelEvent.SearchExperienceShown ->
                fireStep(AppPixelName.ONBOARDING_SEARCH_EXPERIENCE, PIXEL_EVENT_SHOWN)

            is OnboardingPixelEvent.SearchExperienceClicked ->
                fireStep(
                    AppPixelName.ONBOARDING_SEARCH_EXPERIENCE,
                    PIXEL_EVENT_CLICKED,
                    if (event.withAi) SEARCH_PLUS_DUCKAI else SEARCH_ONLY,
                )

            OnboardingPixelEvent.SkipOnboardingShown ->
                fireStep(AppPixelName.ONBOARDING_SKIP_ONBOARDING, PIXEL_EVENT_SHOWN)

            is OnboardingPixelEvent.SkipOnboardingClicked ->
                fireStep(AppPixelName.ONBOARDING_SKIP_ONBOARDING, PIXEL_EVENT_CLICKED, engageOrDismiss(event.engaged))

            OnboardingPixelEvent.NotificationsShown ->
                fireStep(AppPixelName.ONBOARDING_NOTIFICATIONS, PIXEL_EVENT_SHOWN)

            is OnboardingPixelEvent.NotificationsConfirmed ->
                fireStep(
                    AppPixelName.ONBOARDING_NOTIFICATIONS,
                    PIXEL_EVENT_CONFIRMED,
                    if (event.granted) VALUE_GRANTED else VALUE_DENIED,
                )

            OnboardingPixelEvent.QuickSetupShown ->
                fireStep(AppPixelName.ONBOARDING_QUICK_SETUP, PIXEL_EVENT_SHOWN)

            is OnboardingPixelEvent.QuickSetupClicked ->
                fireQuickSetupClicked(event.addressBarPosition, event.inputScreenSelected)

            OnboardingPixelEvent.SyncRestoreShown ->
                fireStep(AppPixelName.ONBOARDING_WELCOME, PIXEL_EVENT_SHOWN)

            is OnboardingPixelEvent.SyncRestoreClicked ->
                fireStep(AppPixelName.ONBOARDING_WELCOME, PIXEL_EVENT_CLICKED, engageOrDismiss(event.engaged))

            OnboardingPixelEvent.TryASearchShown ->
                fireStep(AppPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE, PIXEL_EVENT_SHOWN)

            is OnboardingPixelEvent.TryASearchClicked ->
                fireStep(
                    AppPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE,
                    PIXEL_EVENT_CLICKED,
                    tryASearchValue(event.fromSuggestion, event.isChat),
                )

            OnboardingPixelEvent.AiComparisonShown ->
                fireStep(AppPixelName.ONBOARDING_AI_INTRO, PIXEL_EVENT_SHOWN)

            OnboardingPixelEvent.AiComparisonClicked ->
                fireStep(AppPixelName.ONBOARDING_AI_INTRO, PIXEL_EVENT_CLICKED)
        }
    }

    private fun fireQuickSetupClicked(
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
                pixel = AppPixelName.ONBOARDING_QUICK_SETUP,
                parameters = params,
                type = Unique(tag = "${AppPixelName.ONBOARDING_QUICK_SETUP.pixelName}_$PIXEL_EVENT_CLICKED"),
            )
        }
    }

    private fun fireStep(
        pixelName: AppPixelName,
        event: String,
        value: String? = null,
    ) {
        appCoroutineScope.launch {
            val params = buildStandardParams().toMutableMap()
            params[PIXEL_PARAM_EVENT] = event
            value?.let { params[PIXEL_PARAM_VALUE] = it }
            val tag = buildString {
                append(pixelName.pixelName).append("_").append(event)
                value?.let { append("_").append(it) }
            }
            pixel.fire(pixel = pixelName, parameters = params, type = Unique(tag = tag))
        }
    }

    private suspend fun buildStandardParams(): Map<String, String> {
        // source/flow are install-level facts: CustomAiOnboardingStore is the canonical source (a
        // side-effect-free read of the decision persisted at plan build time).
        val reinstall = isReinstallUser.await()
        val (days, isCustomAiFlow, variant) = withContext(dispatchers.io()) {
            Triple(
                appInstallStore.daysInstalled(),
                customAiOnboardingStore.isEnabled(),
                when (onboardingStore.getOnboardingVariant()) {
                    "search" -> VARIANT_SEARCH
                    "chat" -> VARIANT_CHAT
                    else -> null
                },
            )
        }
        val params = mutableMapOf(
            PIXEL_PARAM_INSTALL_TYPE to if (reinstall) INSTALL_TYPE_REINSTALL else INSTALL_TYPE_NEW,
            PIXEL_PARAM_SOURCE to if (isCustomAiFlow) SOURCE_DUCKAI_CPP else ONBOARDING_DEFAULT,
            PIXEL_PARAM_FLOW to if (isCustomAiFlow) FLOW_DUCKAI else ONBOARDING_DEFAULT,
            PIXEL_PARAM_PIXEL_SOURCE to deviceInfo.formFactor().description,
        )
        variant?.let { params[PIXEL_PARAM_VARIANT] = it }
        if (days in 0..MAX_DAYS_SINCE_INSTALL_REPORTED) {
            params[PIXEL_PARAM_DAYS_SINCE_INSTALL] = days.toString()
        }
        return params
    }

    private fun engageOrDismiss(engaged: Boolean): String = if (engaged) VALUE_ENGAGE else VALUE_DISMISS

    private fun tryASearchValue(fromSuggestion: Boolean, isChat: Boolean): String {
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
        private const val PIXEL_PARAM_EVENT = "e"
        private const val PIXEL_PARAM_VALUE = "value"
        private const val PIXEL_PARAM_INSTALL_TYPE = "it"
        private const val PIXEL_PARAM_DAYS_SINCE_INSTALL = "d"
        private const val PIXEL_PARAM_SOURCE = "source"
        private const val PIXEL_PARAM_FLOW = "flow"
        private const val PIXEL_PARAM_VARIANT = "variant"
        private const val PIXEL_PARAM_PIXEL_SOURCE = "pixelSource"

        private const val PIXEL_EVENT_SHOWN = "shown"
        private const val PIXEL_EVENT_CLICKED = "clicked"
        private const val PIXEL_EVENT_CONFIRMED = "confirmed"

        private const val INSTALL_TYPE_NEW = "new"
        private const val INSTALL_TYPE_REINSTALL = "reinstall"

        private const val ONBOARDING_DEFAULT = "default"
        private const val SOURCE_DUCKAI_CPP = "duckai_cpp"
        private const val FLOW_DUCKAI = "duckai"

        private const val VARIANT_SEARCH = "search_plus_duckai-search"
        private const val VARIANT_CHAT = "search_plus_duckai-chat"

        private const val VALUE_ENGAGE = "engage"
        private const val VALUE_DISMISS = "dismiss"
        private const val VALUE_DDG = "ddg"
        private const val VALUE_OTHER = "other"
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

        private const val MAX_DAYS_SINCE_INSTALL_REPORTED = 28L
    }
}
