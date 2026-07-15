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

package com.duckduckgo.adblocking.impl.menu

import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_MENU_DISABLE_TAPPED_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_MENU_DISABLE_TAPPED_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_MENU_ENABLE_TAPPED_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_MENU_ENABLE_TAPPED_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_PICKER_ALWAYS_OFF_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_PICKER_ALWAYS_OFF_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_PICKER_ALWAYS_ON_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_PICKER_ALWAYS_ON_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_PICKER_DISABLE_UNTIL_RELAUNCH_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_PICKER_DISABLE_UNTIL_RELAUNCH_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.store.AdBlockingSessionStore
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface AdBlockingMenuController {

    /**
     * The option currently in effect, used to place the checkmark on the matching sheet row.
     */
    fun currentChoice(): AdBlockingChoice

    /**
     * Applies a selection made in the disable-mode picker to the persisted setting and/or the
     * session override.
     */
    fun onChoiceSelected(choice: AdBlockingChoice)

    /**
     * Handles a tap on the "Enable" browsing-menu row, enabling ad blocking directly without
     * showing the choice sheet.
     */
    fun onEnableTapped()

    /**
     * Handles a tap on the "Disable" browsing-menu row, which opens the disable-mode picker.
     */
    fun onDisableTapped()
}

@ContributesBinding(AppScope::class)
class RealAdBlockingMenuController @Inject constructor(
    private val settingsRepository: AdBlockingSettingsRepository,
    private val sessionStore: AdBlockingSessionStore,
    private val statusChecker: AdBlockingStatusChecker,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
) : AdBlockingMenuController {

    override fun currentChoice(): AdBlockingChoice = when (statusChecker.currentState()) {
        is AdBlockingState.Enabled -> AdBlockingChoice.ALWAYS_ON
        AdBlockingState.Disabled.UntilRelaunch -> AdBlockingChoice.DISABLE_UNTIL_RELAUNCH
        else -> AdBlockingChoice.ALWAYS_OFF
    }

    override fun onChoiceSelected(choice: AdBlockingChoice) {
        when (choice) {
            AdBlockingChoice.ALWAYS_ON -> {
                pixel.fire(AD_BLOCKING_PICKER_ALWAYS_ON_DAILY, type = Pixel.PixelType.Daily())
                pixel.fire(AD_BLOCKING_PICKER_ALWAYS_ON_COUNT)
            }
            AdBlockingChoice.DISABLE_UNTIL_RELAUNCH -> {
                pixel.fire(AD_BLOCKING_PICKER_DISABLE_UNTIL_RELAUNCH_DAILY, type = Pixel.PixelType.Daily())
                pixel.fire(AD_BLOCKING_PICKER_DISABLE_UNTIL_RELAUNCH_COUNT)
            }
            AdBlockingChoice.ALWAYS_OFF -> {
                pixel.fire(AD_BLOCKING_PICKER_ALWAYS_OFF_DAILY, type = Pixel.PixelType.Daily())
                pixel.fire(AD_BLOCKING_PICKER_ALWAYS_OFF_COUNT)
            }
        }
        apply(choice)
    }

    override fun onEnableTapped() {
        pixel.fire(AD_BLOCKING_MENU_ENABLE_TAPPED_DAILY, type = Pixel.PixelType.Daily())
        pixel.fire(AD_BLOCKING_MENU_ENABLE_TAPPED_COUNT)
        apply(AdBlockingChoice.ALWAYS_ON)
    }

    override fun onDisableTapped() {
        pixel.fire(AD_BLOCKING_MENU_DISABLE_TAPPED_DAILY, type = Pixel.PixelType.Daily())
        pixel.fire(AD_BLOCKING_MENU_DISABLE_TAPPED_COUNT)
    }

    private fun apply(choice: AdBlockingChoice) {
        appScope.launch(dispatcherProvider.io()) {
            when (choice) {
                AdBlockingChoice.ALWAYS_ON -> {
                    settingsRepository.setEnabled(true)
                    sessionStore.clear()
                }
                AdBlockingChoice.DISABLE_UNTIL_RELAUNCH -> sessionStore.setDisabledUntilRelaunch()
                AdBlockingChoice.ALWAYS_OFF -> {
                    settingsRepository.setEnabled(false)
                    sessionStore.clear()
                }
            }
        }
    }
}
