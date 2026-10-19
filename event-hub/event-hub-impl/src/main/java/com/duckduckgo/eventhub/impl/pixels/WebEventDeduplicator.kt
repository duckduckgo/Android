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

package com.duckduckgo.eventhub.impl.pixels

import org.json.JSONObject

/**
 * Decides once, when a web event reaches the hub and before any handler runs, whether it is the first occurrence of its
 * (type, data) on the tab's current page. Keys are cleared when the tab navigates to a different URL.
 *
 * Thread-safe: navigation arrives on the UI thread and events on the JS messaging thread.
 */
internal class WebEventDeduplicator {

    private val lock = Any()
    private val seenKeysByWebView = HashMap<String, MutableSet<Pair<String, String>>>()
    private val currentUrlByWebView = HashMap<String, String>()

    /**
     * @param data the event's `data` value, or null when it was omitted (which is treated as `{}`)
     * @param webViewId an empty id means the event has no tab, so it is never de-duplicated
     */
    fun isFirstOccurrence(type: String, data: Any?, webViewId: String): Boolean {
        if (webViewId.isEmpty()) return true
        val key = type to canonicalJson(data ?: JSONObject())
        return synchronized(lock) { seenKeysByWebView.getOrPut(webViewId) { HashSet() }.add(key) }
    }

    /** @return true if the tab moved to a different URL and its keys were cleared */
    fun onNavigation(webViewId: String, url: String): Boolean {
        if (webViewId.isEmpty() || url.isEmpty()) return false
        synchronized(lock) {
            val previousUrl = currentUrlByWebView.put(webViewId, url)
            if (previousUrl == null || previousUrl == url) return false
            seenKeysByWebView.remove(webViewId)
            return true
        }
    }

    fun clear() {
        synchronized(lock) {
            seenKeysByWebView.clear()
            currentUrlByWebView.clear()
        }
    }
}
