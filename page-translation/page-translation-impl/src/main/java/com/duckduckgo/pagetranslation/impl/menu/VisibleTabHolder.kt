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

package com.duckduckgo.pagetranslation.impl.menu

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Knows which tab is on screen and exposes its translation menu row. The menu and its dot are app-wide,
 * so they read this and never a tab.
 */
interface VisibleTabHolder {
    /**
     * The row of the visible tab, [TranslationMenuViewState.Hidden] when no tab is visible.
     */
    val viewState: StateFlow<TranslationMenuViewState>

    /**
     * Makes [tab] the visible one.
     */
    fun show(tab: TabMenu)

    /**
     * Does nothing unless [tab] is still the visible one, so a tab hiding late cannot clear the tab that replaced it.
     */
    fun hide(tab: TabMenu)

    /**
     * Forwards a tap on the row to the visible tab.
     */
    fun onRowTapped()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealVisibleTabHolder @Inject constructor(
    @AppCoroutineScope private val appScope: CoroutineScope,
) : VisibleTabHolder {

    private val visible = MutableStateFlow<TabMenu?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val viewState: StateFlow<TranslationMenuViewState> = visible
        .flatMapLatest { it?.viewState ?: flowOf(TranslationMenuViewState.Hidden) }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.Eagerly, TranslationMenuViewState.Hidden)

    override fun show(tab: TabMenu) {
        visible.value = tab
    }

    override fun hide(tab: TabMenu) {
        visible.compareAndSet(tab, null)
    }

    override fun onRowTapped() {
        appScope.launch { visible.value?.onRowTapped() }
    }
}
