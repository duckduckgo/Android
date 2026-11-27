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
import kotlinx.coroutines.CoroutineScope

/**
 * Rich Duck.ai card for the tab switcher grid. Each card resolves and owns what it shows, so hosts only
 * decide that a Duck.ai tab uses this card and delegate rendering to it.
 */
interface DuckAiTabGridItemRenderer {
    /** The card loads its content in [coroutineScope], which must end before the host's views are destroyed. */
    fun create(context: Context, coroutineScope: CoroutineScope): DuckAiTabGridItemView
}

/** Identifies the Duck.ai tab a card shows. A different key means the card resolves its content again. */
data class DuckAiTabGridItemKey(
    val tabId: String,
    val url: String?,
)

interface DuckAiTabGridItemView {
    val view: View

    /**
     * Shows the tab identified by [key], loading its content when it isn't already known. A chat that can't be read
     * falls back to [DuckAiTabGridItemState.screenshotPath]. Safe to call repeatedly as the host recycles the view.
     */
    fun bind(key: DuckAiTabGridItemKey, state: DuckAiTabGridItemState, callbacks: DuckAiTabGridItemCallbacks)
}

/** Host-owned state for the card. */
data class DuckAiTabGridItemState(
    val isUnread: Boolean,
    val selection: Selection,
    /** The host's title for the tab, shown while the card's own content loads. */
    val title: String,
    /** Absolute path of the host's screenshot of the tab, or null when it has none. */
    val screenshotPath: String?,
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
