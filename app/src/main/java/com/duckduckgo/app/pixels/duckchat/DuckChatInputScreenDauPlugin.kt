/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.pixels.duckchat

import com.duckduckgo.app.browser.newaddressbaroption.NewAddressBarPickerDataStore
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName.DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires once per Duck.ai DAU day while the input screen setting was never written. The parameters split that population
 * by how the user got there, so the address bar toggle migration can tell "never asked" apart from "said no".
 */
@ContributesIntoSet(AppScope::class)
class DuckChatInputScreenDauPlugin @Inject constructor(
    private val duckChat: DuckChat,
    private val newAddressBarPickerDataStore: NewAddressBarPickerDataStore,
    private val userStageStore: UserStageStore,
    private val appBuildConfig: AppBuildConfig,
    private val pixel: Pixel,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : AtbLifecyclePlugin {

    override fun onDuckAiRetentionAtbRefreshed(
        oldAtb: String,
        newAtb: String,
        metadata: Map<String, String?>,
    ) {
        if (oldAtb.atbAsNumber() == newAtb.atbAsNumber()) return
        coroutineScope.launch(dispatcherProvider.io()) {
            if (!duckChat.isInputScreenEverEnabled()) {
                pixel.fire(DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED, parameters = neverEnabledParams())
            }
        }
    }

    private suspend fun neverEnabledParams(): Map<String, String> = mapOf(
        PARAM_PICKER_WAS_SHOWN to newAddressBarPickerDataStore.wasShown().toString(),
        PARAM_PICKER_DISPLAY_COUNT to displayCountBucket(newAddressBarPickerDataStore.displayCount()),
        PARAM_IS_REINSTALL to appBuildConfig.isAppReinstall().toString(),
        PARAM_APP_STAGE to appStageValue(userStageStore.getUserAppStage()),
    )

    private fun displayCountBucket(count: Int): String = when {
        count <= 0 -> "0"
        count == 1 -> "1"
        else -> "2_plus"
    }

    private fun appStageValue(stage: AppStage): String = when (stage) {
        AppStage.NEW -> "new"
        AppStage.DAX_ONBOARDING -> "dax_onboarding"
        AppStage.ESTABLISHED -> "established"
    }

    /**
     * Converts an ATB version string (e.g. "v123-2ma") to a monotonically increasing integer.
     * The variant suffix (e.g. "ma") is intentionally ignored so that strings representing the
     * same week/day but different cohort tags compare as equal.
     */
    internal fun String.atbAsNumber(): Int {
        val startIndex = indexOf('v') + 1
        val endIndex = indexOf('-', startIndex)
        val week = if (endIndex > 0 && startIndex < endIndex) {
            substring(startIndex, endIndex).toIntOrNull() ?: 0
        } else {
            substringAfter('v', "").toIntOrNull() ?: 0
        }
        val day = substringAfterLast('-', "").firstOrNull()?.digitToIntOrNull() ?: 0
        return week * 7 + day - 1
    }

    private companion object {
        const val PARAM_PICKER_WAS_SHOWN = "picker_was_shown"
        const val PARAM_PICKER_DISPLAY_COUNT = "picker_display_count"
        const val PARAM_IS_REINSTALL = "is_reinstall"
        const val PARAM_APP_STAGE = "app_stage"
    }
}
