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

package com.duckduckgo.duckchat.api.inputscreen

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.plugins.ActivePlugin
import kotlinx.coroutines.CoroutineScope

/**
 * Lets a module contribute its own item(s) to the native-input Chat-tab suggestions list without
 * depending on `duckchat-impl`. The host slots each contribution's [RecyclerView.Adapter]s into the
 * Chat-tab's `ConcatAdapter`.
 *
 * The plugin is a singleton in the DI graph (it is multibound); it acts as a factory because the
 * contributed adapters are stateful and bound to a single Chat-tab presentation. Implement with
 * `@ContributesActivePlugin`, set `parentFeatureName = "pluginPointNativeInputChatTabItemPlugin"`, and
 * set `priority` to one of the [companion] constants to order against other contributions.
 *
 * For the common case of a single static view (e.g. a card), extend [SingleViewChatTabItem] instead
 * of implementing [NativeInputChatTabItem] directly.
 */
interface NativeInputChatTabItemPlugin : ActivePlugin {

    /**
     * Create a fresh item bound to one Chat-tab presentation.
     *
     * @param context a view context suitable for inflating the item's views.
     * @param scope cancelled by the host when the Chat tab is torn down. Use it to collect data into
     * the returned adapters; do not retain it beyond the returned [NativeInputChatTabItem].
     * @param browserMode the hosting activity's frozen browser mode, for contributions whose
     * eligibility is mode-dependent.
     */
    fun create(context: Context, scope: CoroutineScope, browserMode: BrowserMode): NativeInputChatTabItem

    companion object {
        // Order of contributions, top to bottom. Lower value comes first (higher in the list); gaps
        // leave room to insert new contributions; equal values are allowed and tie-break by class name.
        // Mirrors NewTabPageSectionPlugin. The built-in sections will claim their own constants here as
        // they migrate onto this plugin point; until then they render below all contributions.
        const val PRIORITY_PROMO = 100
        const val PRIORITY_CHAT_HISTORY = 200
        const val PRIORITY_URL_SUGGESTIONS = 300
        const val PRIORITY_SEARCH_FOR = 400
    }
}

/**
 * A single contribution to the Chat-tab list. Purely a rendering surface: it owns its adapter(s) and
 * view holders, and the host only positions them.
 *
 * Input state the item needs to decide its rows — the current query, the displayed mode — is read from
 * the shared state in this module (e.g. [com.duckduckgo.duckchat.api.DuckChatInputModeState.inputQuery]
 * and `displayedMode`), not pushed in here. An item observes that state (combining it with its own
 * async data) and updates its adapter(s); an item with no rows contributes no content and does not keep
 * the suggestions overlay open. For the common single-view card see [SingleViewChatTabItem].
 */
interface NativeInputChatTabItem {

    /**
     * The adapters slotted into the host's `ConcatAdapter`, in order. Most items contribute a single
     * adapter; returning several lets one item own multiple sections without nesting `ConcatAdapter`s.
     * The plugin fully owns each adapter's item count and view holders.
     */
    val adapters: List<RecyclerView.Adapter<*>>
}
