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

package com.duckduckgo.app.global.shortcut

import android.content.Intent
import android.os.Bundle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.ManualDataClearing
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import dev.zacsweers.metro.HasMemberInjections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

/**
 * Entry point for the "Clear Data" launcher shortcut. `exported=false` — reachable only via the
 * system ShortcutService
 */
@HasMemberInjections
@InjectWith(ActivityScope::class)
class FireShortcutTrampolineActivity : DuckDuckGoActivity() {

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var androidBrowserConfigFeature: AndroidBrowserConfigFeature

    @Inject lateinit var fireDataStore: FireDataStore

    @Inject lateinit var dataClearingWideEvent: DataClearingWideEvent

    @Inject lateinit var dataClearing: ManualDataClearing

    @Inject lateinit var clearDataAction: ClearDataAction

    @Inject lateinit var settingsDataStore: SettingsDataStore

    @Inject lateinit var currentBrowserMode: BrowserMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logcat(INFO) { "Clearing everything as a result of the Clear Data launcher shortcut" }
        appCoroutineScope.launch(dispatcherProvider.io()) {
            // Kill switch off: delegate to BrowserActivity so already-migrated pinned shortcuts still honor the flag flip.
            if (!androidBrowserConfigFeature.useFireAppShortcutTrampoline().isEnabled()) {
                logcat(INFO) { "useFireAppShortcutTrampoline kill switch is off; delegating Clear Data shortcut to BrowserActivity" }
                val delegate = Intent(this@FireShortcutTrampolineActivity, BrowserActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(BrowserActivity.PERFORM_FIRE_ON_ENTRY_EXTRA, true)
                }
                startActivity(delegate)
                return@launch
            }
            if (androidBrowserConfigFeature.singleTabFireDialog().isEnabled()) {
                val clearOptions = fireDataStore.getManualClearOptions()
                dataClearingWideEvent.start(
                    entryPoint = DataClearingWideEvent.EntryPoint.APP_SHORTCUT,
                    clearOptions = clearOptions,
                )
                try {
                    dataClearing.clearDataUsingManualFireOptions(shouldRestartIfRequired = true, browserMode = currentBrowserMode)
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
        finish()
    }
}
