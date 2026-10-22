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

package com.duckduckgo.duckchat.api.tabswitcher

import android.content.Context
import android.view.View
import com.duckduckgo.browsermode.api.BrowserMode

/**
 * Rich Duck.ai card for the tab switcher grid. Content is resolved and owned by Duck.ai;
 * the host decides layout from whether a card is available and delegates rendering.
 */
interface DuckAiTabGridItemRenderer {
    /**
     * Resolves what a Duck.ai tab's grid item shows, from native chat storage for [mode].
     * @return the content, or null when it can't be shown and the host should use its existing card
     */
    suspend fun resolve(tabId: String, url: String?, mode: BrowserMode): DuckAiTabGridItemContent?

    fun create(context: Context): DuckAiTabGridItemView
}

/**
 * What a Duck.ai tab's grid item shows, resolved by Duck.ai and opaque to hosts. Hosts only store it,
 * compare it and pass it to [DuckAiTabGridItemView.bind]. Equality reflects what is shown, so an
 * unequal value means the item needs rebinding.
 */
interface DuckAiTabGridItemContent {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

interface DuckAiTabGridItemView {
    val view: View

    /** Safe to call repeatedly as the host recycles the view. */
    fun bind(content: DuckAiTabGridItemContent, state: DuckAiTabGridItemState, callbacks: DuckAiTabGridItemCallbacks)
}

/** Host-owned state for the card. */
data class DuckAiTabGridItemState(
    val isUnread: Boolean,
    val selection: Selection,
) {
    sealed interface Selection {
        data object None : Selection
        data class Selectable(val isSelected: Boolean) : Selection
    }
}

interface DuckAiTabGridItemCallbacks {
    fun onClick()
    fun onClose()
}
