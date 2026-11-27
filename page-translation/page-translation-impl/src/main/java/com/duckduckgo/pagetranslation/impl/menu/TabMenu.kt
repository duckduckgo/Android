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

import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pagetranslation.api.TabContent
import com.duckduckgo.pagetranslation.impl.translate.TranslateRequest
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * The translation row of one tab, registered with [VisibleTabHolder] while the tab is the one on screen.
 */
@SingleIn(FragmentScope::class)
class TabMenu @Inject constructor(
    private val source: TranslationMenuSource,
    private val translateRequest: TranslateRequest,
    private val visibleTab: VisibleTabHolder,
    private val resolver: TranslationMenuViewStateResolver,
) {
    private val content = MutableStateFlow(TabContent.Other)
    private var visible = false

    private val snapshot: Flow<TabTranslationSnapshot> = combine(source.menuOffer, source.pair, source.state, ::TabTranslationSnapshot)

    val viewState: Flow<TranslationMenuViewState> = combine(content, snapshot, resolver::resolve).distinctUntilChanged()

    private fun current(): TranslationMenuViewState = resolver.resolve(
        content.value,
        TabTranslationSnapshot(source.menuOffer.value, source.pair.value, source.state.value),
    )

    fun onTabStateChanged(isVisible: Boolean, content: TabContent) {
        this.content.value = content
        visible = isVisible
        if (isVisible) visibleTab.show(this) else visibleTab.hide(this)
    }

    fun onAttached() {
        if (visible) visibleTab.show(this)
    }

    fun onDetached() {
        visibleTab.hide(this)
    }

    fun onRowTapped() {
        when (val viewState = current()) {
            is TranslationMenuViewState.Translate -> if (viewState.enabled) source.pair.value?.let(translateRequest::translate)
            TranslationMenuViewState.ShowOriginal -> source.showOriginal()
            TranslationMenuViewState.InProgress, TranslationMenuViewState.Hidden -> Unit
        }
    }
}
