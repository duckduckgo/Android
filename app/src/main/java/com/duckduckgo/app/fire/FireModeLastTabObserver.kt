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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

/**
 * Keeps a state Fire mode has browsing data that still needs burning.
 *
 * Set while a Fire tab exists, cleared once a burn runs. [FireModeLastTabObserver] needs it because a burn
 * deletes the Fire tabs itself, which would otherwise look exactly like the user closing the last tab and
 * start a second burn.
 */
@SingleInstanceIn(AppScope::class)
class FireModeDataClearingState @Inject constructor() {
    private val _hasUnclearedData = MutableStateFlow(false)
    val hasUnclearedData: StateFlow<Boolean> = _hasUnclearedData.asStateFlow()

    fun markDataForClearing() {
        _hasUnclearedData.value = true
    }

    fun onDataCleared() {
        _hasUnclearedData.value = false
    }
}

/**
 * Burns Fire mode data once the last Fire tab is gone.
 */
@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class FireModeLastTabObserver @Inject constructor(
    @param:FireMode private val fireTabsDao: TabsDao,
    @param:FireMode private val fireTabRepository: TabRepository,
    private val adClickManager: AdClickManager,
    private val dataClearing: ManualDataClearing,
    private val dataClearingWideEvent: DataClearingWideEvent,
    private val fireModeDataClearingState: FireModeDataClearingState,
    private val fireModeAvailability: FireModeAvailability,
    @param:AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            commitPendingFireCloses()
            if (!fireModeAvailability.isAvailable()) return@launch

            fireTabsDao.flowTabCount()
                .map { it == 0 }
                .distinctUntilChanged()
                .collect { fireModeEmpty ->
                    if (!fireModeEmpty) {
                        fireModeDataClearingState.markDataForClearing()
                    } else if (fireModeDataClearingState.hasUnclearedData.value) {
                        burnFireData()
                    }
                }
        }
    }

    // Fire mode's startup tab purge, which marks the data for auto-burn
    override fun onStart(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            commitPendingFireCloses()
        }
    }

    private suspend fun commitPendingFireCloses() {
        val pendingCloses = fireTabRepository.getDeletableTabIds()
        if (pendingCloses.isEmpty()) return

        pendingCloses.forEach { adClickManager.clearTabId(it) }
        if (fireModeAvailability.isAvailable()) {
            fireModeDataClearingState.markDataForClearing()
        }
        fireTabRepository.purgeDeletableTabs()
    }

    private suspend fun burnFireData() {
        dataClearingWideEvent.start(
            entryPoint = DataClearingWideEvent.EntryPoint.FIRE_TABS_EMPTIED,
            clearOptions = setOf(
                FireClearOption.TABS,
                FireClearOption.DATA,
                FireClearOption.DUCKAI_CHATS,
            ),
            browserMode = BrowserMode.FIRE,
        )
        try {
            dataClearing.clearDataUsingManualFireOptions(
                shouldRestartIfRequired = false,
                browserMode = BrowserMode.FIRE,
            )
            dataClearingWideEvent.finishSuccess()
        } catch (e: Exception) {
            logcat(WARN) { "Failed to burn Fire data after the last Fire tab was closed: ${e.asLog()}" }
            dataClearingWideEvent.finishFailure(e)
        }
    }
}
