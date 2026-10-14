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

package com.duckduckgo.app.browser.session

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiSessionCallback
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Watches the active browser mode's selected tab and reports every raw emission [DuckAiSessionCallback].
 * All interpretation lives in the coordinator; this class is just an adapter from the tab repository's Flow to that API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class, boundType = BrowserLifecycleObserver::class)
class BrowserSessionTabObserver @Inject constructor(
    private val tabRepositoryProvider: BrowserModeDataProvider<TabRepository>,
    private val browserModeStateHolder: BrowserModeStateHolder,
    private val duckAiSessionCallback: DuckAiSessionCallback,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : BrowserLifecycleObserver {

    // This is a genuine mode-transition observer: a Duck.ai session can be active in either mode, so this must follow the active mode's selected tab.
    @Suppress("DenyListedApi")
    private val currentMode = browserModeStateHolder.currentMode

    init {
        currentMode
            .flatMapLatest { mode -> tabRepositoryProvider.forMode(mode).flowSelectedTab }
            .onEach { tab -> duckAiSessionCallback.onSelectedTabChanged(tab?.tabId, tab?.url) }
            .launchIn(appCoroutineScope)
    }
}
