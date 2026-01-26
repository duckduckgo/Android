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

package com.duckduckgo.app.browser.menu

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

/**
 * Manages the display settings for the browser menu, including the experimental menu option.
 * Provides reactive flows to observe the state of the experimental menu option.
 * Allows updating the experimental menu setting.
 */
interface BrowserMenuDisplayRepository {
    /**
     * Reactive flow of the experimental menu enabled state.
     */
    val browserMenuState: Flow<BrowserMenuDisplayState>

    /**
     * Sets the experimental menu enabled state.
     *
     * @param enabled true to enable the experimental menu, false to disable it
     */
    suspend fun setExperimentalMenuEnabled(enabled: Boolean)
}

data class BrowserMenuDisplayState(
    val hasOption: Boolean,
    val isEnabled: Boolean,
)

@ContributesBinding(
    scope = AppScope::class,
    boundType = BrowserMenuDisplayRepository::class,
)
@SingleInstanceIn(AppScope::class)
class RealBrowserMenuDisplayRepository @Inject constructor(
    private val browserMenuStore: SettingsDataStore,
    private val browserConfigFeature: AndroidBrowserConfigFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : BrowserMenuDisplayRepository {
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 0)
    override val browserMenuState: Flow<BrowserMenuDisplayState> = flow {
        val isActivate = browserConfigFeature.experimentalBrowsingMenu().isEnabled()
        emit(
            BrowserMenuDisplayState(
                hasOption = isActivate,
                isEnabled = browserMenuStore.useBottomSheetMenu,
            ),
        )
        refreshTrigger.collect {
            emit(
                BrowserMenuDisplayState(
                    hasOption = isActivate,
                    isEnabled = browserMenuStore.useBottomSheetMenu,
                ),
            )
        }
    }
        .distinctUntilChanged()
        .shareIn(
            scope = appCoroutineScope,
            replay = 1,
            started = SharingStarted.WhileSubscribed(),
        )

    override suspend fun setExperimentalMenuEnabled(enabled: Boolean) {
        browserMenuStore.useBottomSheetMenu = enabled
        refreshTrigger.emit(Unit)
    }
}
