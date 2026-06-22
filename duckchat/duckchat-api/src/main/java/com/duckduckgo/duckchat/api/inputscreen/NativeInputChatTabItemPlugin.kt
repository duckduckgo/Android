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
import com.duckduckgo.common.utils.plugins.ActivePlugin
import kotlinx.coroutines.CoroutineScope

/**
 * Lets a module contribute its own item(s) to the native-input Chat-tab suggestions list without
 * depending on `duckchat-impl`. The host slots each contribution's [RecyclerView.Adapter] into the
 * Chat-tab's `ConcatAdapter`.
 *
 * The plugin is a singleton in the DI graph (it is multibound); it acts as a factory because the
 * contributed adapter is stateful and bound to a single Chat-tab presentation. Implement with
 * `@ContributesActivePlugin`, set `parentFeatureName = "pluginPointNativeInputChatTabItemPlugin"`, and
 * use `priority` to order against other contributions (lower priority comes first / higher in the
 * list).
 */
interface NativeInputChatTabItemPlugin : ActivePlugin {

    /**
     * Create a fresh item bound to one Chat-tab presentation.
     *
     * @param context a view context suitable for inflating the item's views.
     * @param scope cancelled by the host when the Chat tab is torn down. Use it to collect data into
     * the returned adapter; do not retain it beyond the returned [NativeInputChatTabItem].
     */
    fun create(context: Context, scope: CoroutineScope): NativeInputChatTabItem
}

/**
 * A single contribution to the Chat-tab list. Owns its adapter and its view holders; the host only
 * positions it and (for query-driven items) forwards the current query.
 */
interface NativeInputChatTabItem {

    /**
     * The adapter slotted into the host's `ConcatAdapter`. The plugin fully owns its item count and
     * view holders. An empty adapter (`itemCount == 0`) contributes no content and does not keep the
     * suggestions overlay open.
     */
    val adapter: RecyclerView.Adapter<*>

    /**
     * Whether this item participates in query filtering.
     *
     * `false` (e.g. a message card): the item belongs to the empty/zero state. The host shows it only
     * while the query is empty and hides it as soon as the user starts typing; [onQueryChanged] is
     * never called. The item updates its own content from its own data source.
     *
     * `true`: the item stays visible while the user types and the host calls [onQueryChanged] with the
     * current query so the item can filter itself.
     */
    val supportsQuery: Boolean

    /**
     * Called with the current query whenever it changes, but only when [supportsQuery] is `true`.
     * Default is a no-op so static items don't need to implement it.
     */
    fun onQueryChanged(query: String) {}
}
