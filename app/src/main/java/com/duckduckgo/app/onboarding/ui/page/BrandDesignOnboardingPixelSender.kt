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
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface BrandDesignOnboardingPixelSender {
    fun fireWelcomeShown(isReinstallUser: Boolean)
    fun fireWelcomeClicked(isReinstallUser: Boolean, engaged: Boolean)
    fun fireSetDefaultShown(isReinstallUser: Boolean)
    fun fireSetDefaultClicked(isReinstallUser: Boolean)
    fun fireSetDefaultConfirmed(isReinstallUser: Boolean, isDdgDefault: Boolean)
    fun fireAddressBarPositionShown(isReinstallUser: Boolean)
    fun fireAddressBarPositionClicked(isReinstallUser: Boolean, position: OmnibarType)
    fun fireSearchExperienceShown(isReinstallUser: Boolean)
    fun fireSearchExperienceClicked(isReinstallUser: Boolean, withAi: Boolean)
    fun fireSkipOnboardingShown(isReinstallUser: Boolean)
    fun fireSkipOnboardingClicked(isReinstallUser: Boolean, engaged: Boolean)
    fun fireNotificationsShown(isReinstallUser: Boolean)
    fun fireNotificationsConfirmed(isReinstallUser: Boolean, granted: Boolean)
    fun fireQuickSetupShown(isReinstallUser: Boolean)
    fun fireQuickSetupClicked(
        isReinstallUser: Boolean,
        addressBarPosition: OmnibarType,
        inputScreenSelected: Boolean,
    )
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrandDesignOnboardingPixelSender @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val appInstallStore: AppInstallStore,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val deviceInfo: DeviceInfo,
) : BrandDesignOnboardingPixelSender {

    override fun fireWelcomeShown(isReinstallUser: Boolean) =
        fireStep(AppPixelName.ONBOARDING_WELCOME, PIXEL_EVENT_SHOWN, isReinstallUser = isReinstallUser)

    override fun fireWelcomeClicked(isReinstallUser: Boolean, engaged: Boolean) =
        fireStep(AppPixelName.ONBOARDING_WELCOME, PIXEL_EVENT_CLICKED, engageOrDismiss(engaged), isReinstallUser)

    override fun fireSetDefaultShown(isReinstallUser: Boolean) =
        fireStep(AppPixelName.ONBOARDING_SET_DEFAULT, PIXEL_EVENT_SHOWN, isReinstallUser = isReinstallUser)

    override fun fireSetDefaultClicked(isReinstallUser: Boolean) =
        fireStep(AppPixelName.ONBOARDING_SET_DEFAULT, PIXEL_EVENT_CLICKED, isReinstallUser = isReinstallUser)

    override fun fireSetDefaultConfirmed(isReinstallUser: Boolean, isDdgDefault: Boolean) =
        fireStep(
            AppPixelName.ONBOARDING_SET_DEFAULT,
            PIXEL_EVENT_CONFIRMED,
            if (isDdgDefault) VALUE_DDG else VALUE_OTHER,
            isReinstallUser,
        )

    override fun fireAddressBarPositionShown(isReinstallUser: Boolean) =
        fireStep(AppPixelName.ONBOARDING_ADDRESS_BAR_POSITION, PIXEL_EVENT_SHOWN, isReinstallUser = isReinstallUser)

    override fun fireAddressBarPositionClicked(isReinstallUser: Boolean, position: OmnibarType) =
        fireStep(AppPixelName.ONBOARDING_ADDRESS_BAR_POSITION, PIXEL_EVENT_CLICKED, addressBarValue(position), isReinstallUser)

    override fun fireSearchExperienceShown(isReinstallUser: Boolean) =
        fireStep(AppPixelName.ONBOARDING_SEARCH_EXPERIENCE, PIXEL_EVENT_SHOWN, isReinstallUser = isReinstallUser)

    override fun fireSearchExperienceClicked(isReinstallUser: Boolean, withAi: Boolean) =
        fireStep(
            AppPixelName.ONBOARDING_SEARCH_EXPERIENCE,
            PIXEL_EVENT_CLICKED,
            if (withAi) SEARCH_PLUS_DUCKAI else SEARCH_ONLY,
            isReinstallUser,
        )

    override fun fireSkipOnboardingShown(isReinstallUser: Boolean) =
        fireStep(AppPixelName.ONBOARDING_SKIP_ONBOARDING, PIXEL_EVENT_SHOWN, isReinstallUser = isReinstallUser)

    override fun fireSkipOnboardingClicked(isReinstallUser: Boolean, engaged: Boolean) =
        fireStep(AppPixelName.ONBOARDING_SKIP_ONBOARDING, PIXEL_EVENT_CLICKED, engageOrDismiss(engaged), isReinstallUser)

    override fun fireNotificationsShown(isReinstallUser: Boolean) =
        fireStep(AppPixelName.ONBOARDING_NOTIFICATIONS, PIXEL_EVENT_SHOWN, isReinstallUser = isReinstallUser)

    override fun fireNotificationsConfirmed(isReinstallUser: Boolean, granted: Boolean) =
        fireStep(
            AppPixelName.ONBOARDING_NOTIFICATIONS,
            PIXEL_EVENT_CONFIRMED,
            if (granted) VALUE_GRANTED else VALUE_DENIED,
            isReinstallUser,
        )

    override fun fireQuickSetupShown(isReinstallUser: Boolean) =
        fireStep(AppPixelName.ONBOARDING_QUICK_SETUP, PIXEL_EVENT_SHOWN, isReinstallUser = isReinstallUser)

    override fun fireQuickSetupClicked(
        isReinstallUser: Boolean,
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
            val params = buildStandardParams(isReinstallUser).toMutableMap()
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
        isReinstallUser: Boolean,
    ) {
        appCoroutineScope.launch {
            val params = buildStandardParams(isReinstallUser).toMutableMap()
            params[PIXEL_PARAM_EVENT] = event
            value?.let { params[PIXEL_PARAM_VALUE] = it }
            val tag = buildString {
                append(pixelName.pixelName).append("_").append(event)
                value?.let { append("_").append(it) }
            }
            pixel.fire(pixel = pixelName, parameters = params, type = Unique(tag = tag))
        }
    }

    private suspend fun buildStandardParams(isReinstallUser: Boolean): Map<String, String> {
        val days = withContext(dispatchers.io()) { appInstallStore.daysInstalled() }
        val params = mutableMapOf(
            PIXEL_PARAM_INSTALL_TYPE to if (isReinstallUser) INSTALL_TYPE_REINSTALL else INSTALL_TYPE_NEW,
            PIXEL_PARAM_SOURCE to ONBOARDING_DEFAULT,
            PIXEL_PARAM_FLOW to ONBOARDING_DEFAULT,
            PIXEL_PARAM_PIXEL_SOURCE to deviceInfo.formFactor().description,
        )
        if (days in 0..MAX_DAYS_SINCE_INSTALL_REPORTED) {
            params[PIXEL_PARAM_DAYS_SINCE_INSTALL] = days.toString()
        }
        return params
    }

    private fun engageOrDismiss(engaged: Boolean): String = if (engaged) VALUE_ENGAGE else VALUE_DISMISS

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
        private const val PIXEL_PARAM_PIXEL_SOURCE = "pixelSource"

        private const val PIXEL_EVENT_SHOWN = "shown"
        private const val PIXEL_EVENT_CLICKED = "clicked"
        private const val PIXEL_EVENT_CONFIRMED = "confirmed"

        private const val INSTALL_TYPE_NEW = "new"
        private const val INSTALL_TYPE_REINSTALL = "reinstall"

        private const val ONBOARDING_DEFAULT = "default"

        private const val VALUE_ENGAGE = "engage"
        private const val VALUE_DISMISS = "dismiss"
        private const val VALUE_DDG = "ddg"
        private const val VALUE_OTHER = "other"
        private const val VALUE_GRANTED = "granted"
        private const val VALUE_DENIED = "denied"
        private const val SEARCH_ONLY = "search_only"
        private const val SEARCH_PLUS_DUCKAI = "search_plus_duckai"

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
