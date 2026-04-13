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

package com.duckduckgo.app.browser.menu

import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Combines all sources that can trigger the blue dot on the browser menu icon
 */
interface BrowserMenuHighlightState {
    /**
     * Reactive flow with individual highlight sources.
     */
    val highlightState: StateFlow<HighlightState>

    data class HighlightState(
        val defaultBrowserHighlight: Boolean = false,
        val downloadHighlight: Boolean = false,
    )
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealBrowserMenuHighlightState @Inject constructor(
    additionalDefaultBrowserPrompts: AdditionalDefaultBrowserPrompts,
    downloadMenuStateProvider: DownloadMenuStateProvider,
    browserMenuDisplayRepository: BrowserMenuDisplayRepository,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : BrowserMenuHighlightState {
    override val highlightState: StateFlow<BrowserMenuHighlightState.HighlightState> = combine(
        additionalDefaultBrowserPrompts.highlightPopupMenu,
        downloadMenuStateProvider.hasNewDownloadFlow,
        browserMenuDisplayRepository.browserMenuState.map { it.isEnabled },
    ) { defaultBrowserHighlight, hasNewDownload, isBottomSheetMenu ->
        BrowserMenuHighlightState.HighlightState(
            defaultBrowserHighlight = defaultBrowserHighlight,
            downloadHighlight = hasNewDownload && isBottomSheetMenu,
        )
    }.flowOn(dispatcherProvider.io()).stateIn(appCoroutineScope, SharingStarted.Eagerly, BrowserMenuHighlightState.HighlightState())
}
