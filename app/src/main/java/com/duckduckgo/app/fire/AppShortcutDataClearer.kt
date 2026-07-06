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

package com.duckduckgo.app.fire

import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AppShortcutDataClearer {
    suspend fun clearFromAppShortcut(browserMode: BrowserMode)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAppShortcutDataClearer @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val fireDataStore: FireDataStore,
    private val dataClearingWideEvent: DataClearingWideEvent,
    private val dataClearing: ManualDataClearing,
    private val clearDataAction: ClearDataAction,
    private val settingsDataStore: SettingsDataStore,
) : AppShortcutDataClearer {

    override suspend fun clearFromAppShortcut(browserMode: BrowserMode) {
        if (androidBrowserConfigFeature.singleTabFireDialog().isEnabled()) {
            val clearOptions = fireDataStore.getManualClearOptions()
            dataClearingWideEvent.start(
                entryPoint = DataClearingWideEvent.EntryPoint.APP_SHORTCUT,
                clearOptions = clearOptions,
            )
            try {
                dataClearing.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, browserMode = browserMode)
                dataClearingWideEvent.finishSuccess()
            } catch (e: Exception) {
                dataClearingWideEvent.finishFailure(e)
                throw e
            }
        } else {
            dataClearingWideEvent.startLegacy(
                entryPoint = DataClearingWideEvent.EntryPoint.LEGACY_APP_SHORTCUT,
                clearWhatOption = ClearWhatOption.CLEAR_TABS_AND_DATA,
                clearDuckAiData = settingsDataStore.clearDuckAiData,
            )
            try {
                clearDataAction.clearTabsAndAllDataAsync(appInForeground = true, shouldFireDataClearPixel = true)
                clearDataAction.setAppUsedSinceLastClearFlag(false)
                dataClearingWideEvent.finishSuccess()
            } catch (e: Exception) {
                dataClearingWideEvent.finishFailure(e)
                throw e
            }
            clearDataAction.killAndRestartProcess(notifyDataCleared = false)
        }
    }
}
