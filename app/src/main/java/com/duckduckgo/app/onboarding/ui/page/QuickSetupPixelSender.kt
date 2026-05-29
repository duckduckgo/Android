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
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_QUICK_SETUP
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

/**
 * Dispatches the onboarding quick-setup telemetry, keeping the composite pixel construction
 * out of [BrandDesignUpdatePageViewModel].
 */
interface QuickSetupPixelSender {
    fun fireShown(isReinstallUser: Boolean)
    fun fireClicked(
        isReinstallUser: Boolean,
        addressBarPosition: OmnibarType,
        inputScreenSelected: Boolean,
    )
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealQuickSetupPixelSender @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val appInstallStore: AppInstallStore,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val deviceInfo: DeviceInfo,
) : QuickSetupPixelSender {

    override fun fireShown(isReinstallUser: Boolean) {
        appCoroutineScope.launch {
            val params = buildStandardParams(isReinstallUser) + (PIXEL_PARAM_EVENT to PIXEL_EVENT_SHOWN)
            pixel.fire(
                pixel = ONBOARDING_QUICK_SETUP,
                parameters = params,
                type = Unique(tag = "${ONBOARDING_QUICK_SETUP.pixelName}_$PIXEL_EVENT_SHOWN"),
            )
        }
    }

    override fun fireClicked(
        isReinstallUser: Boolean,
        addressBarPosition: OmnibarType,
        inputScreenSelected: Boolean,
    ) {
        appCoroutineScope.launch {
            val (isDefault, hasWidget) = withContext(dispatchers.io()) {
                defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
            }
            val addressBar = when (addressBarPosition) {
                OmnibarType.SINGLE_TOP -> ADDRESS_BAR_TOP
                OmnibarType.SINGLE_BOTTOM -> ADDRESS_BAR_BOTTOM
                OmnibarType.SPLIT -> ADDRESS_BAR_SPLIT
            }
            val inputType = if (inputScreenSelected) {
                INPUT_TYPE_SEARCH_AND_DUCKAI
            } else {
                INPUT_TYPE_SEARCH
            }
            val value = "$PIXEL_SET_AS_DEFAULT_VALUE_PARAM:${onOff(isDefault)}," +
                "$PIXEL_WIDGET_VALUE_PARAM:${onOff(hasWidget)}," +
                "$PIXEL_ADDRESS_BAR_VALUE_PARAM:$addressBar," +
                "$PIXEL_INPUT_TYPE_VALUE_PARAM:$inputType"
            val params = buildStandardParams(isReinstallUser) + mapOf(
                PIXEL_PARAM_EVENT to PIXEL_EVENT_CLICKED,
                PIXEL_PARAM_VALUE to value,
            )
            pixel.fire(
                pixel = ONBOARDING_QUICK_SETUP,
                parameters = params,
                type = Unique(tag = "${ONBOARDING_QUICK_SETUP.pixelName}_$PIXEL_EVENT_CLICKED"),
            )
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

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private companion object {
        private const val PIXEL_PARAM_EVENT = "e"
        private const val PIXEL_PARAM_VALUE = "value"
        private const val PIXEL_SET_AS_DEFAULT_VALUE_PARAM = "set_as_default"
        private const val PIXEL_WIDGET_VALUE_PARAM = "widget"
        private const val PIXEL_ADDRESS_BAR_VALUE_PARAM = "address_bar"
        private const val PIXEL_INPUT_TYPE_VALUE_PARAM = "input_type"
        private const val PIXEL_PARAM_INSTALL_TYPE = "it"
        private const val PIXEL_PARAM_DAYS_SINCE_INSTALL = "d"
        private const val PIXEL_PARAM_SOURCE = "source"
        private const val PIXEL_PARAM_FLOW = "flow"
        private const val PIXEL_PARAM_PIXEL_SOURCE = "pixelSource"

        private const val PIXEL_EVENT_SHOWN = "shown"
        private const val PIXEL_EVENT_CLICKED = "clicked"

        private const val INSTALL_TYPE_NEW = "new"
        private const val INSTALL_TYPE_REINSTALL = "reinstall"

        private const val ONBOARDING_DEFAULT = "default"

        private const val ADDRESS_BAR_TOP = "top"
        private const val ADDRESS_BAR_BOTTOM = "bottom"
        private const val ADDRESS_BAR_SPLIT = "split"

        private const val INPUT_TYPE_SEARCH = "search"
        private const val INPUT_TYPE_SEARCH_AND_DUCKAI = "search_and_duckai"

        private const val MAX_DAYS_SINCE_INSTALL_REPORTED = 28L
    }
}
