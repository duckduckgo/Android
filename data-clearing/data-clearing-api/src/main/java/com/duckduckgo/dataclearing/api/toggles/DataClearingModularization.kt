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

package com.duckduckgo.dataclearing.api.toggles

/**
 * Whether a data source is cleared by its own [com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin]
 * rather than by the legacy clearing path.
 *
 * Read by both sides: the plugin that owns the source, and the legacy path that would otherwise
 * clear it. Exactly one of the two acts on a given source.
 *
 * Temporary: removed together with the legacy path once every source has shipped at 100%.
 */
interface DataClearingModularization {
    suspend fun isPluginEnabled(source: DataClearingSource): Boolean
}

enum class DataClearingSource {
    /** Browsing history entries and their visits. */
    NAVIGATION_HISTORY,

    /** Prunes tombstoned bookmarks and favorites; only runs when the user is signed in to Sync. */
    SAVED_SITES_PRUNE,

    /** The app cache directory, excluding the WebView, OkHttp and favicon data held inside it. */
    APP_CACHE,

    /** The contextual tab-to-chat bookkeeping behind a per-tab chat clear. */
    CONTEXTUAL_CHATS,

    /** Per-site permission grants. */
    SITE_PERMISSIONS,

    /** Per-site preferences. */
    SITE_PREFERENCES,

    /** Cookies, including third-party and external ones. */
    COOKIES,

    /** Regular-mode tabs; the Fire-mode tab clear already runs through the plugin. */
    TABS,

    /** Regular-mode full burn of WebView storage. */
    WEB_STORAGE,

    /** Regular-mode single-tab burn of WebView storage. */
    WEB_STORAGE_SINGLE_TAB,

    /** The Duck.ai-only storage clear behind "Clear all chats". */
    DUCK_AI_WEB_STORAGE,

    /** The 7-day blocked-tracker log. */
    TRACKER_STATS,
}
