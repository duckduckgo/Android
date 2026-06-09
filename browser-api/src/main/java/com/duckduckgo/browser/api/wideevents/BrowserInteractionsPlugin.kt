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

package com.duckduckgo.browser.api.wideevents

/**
 * Plugin point for browser-control interactions — events that happen anywhere in the browser,
 * regardless of which surface (NTP or LUT) is visible.
 */
interface BrowserInteractionsPlugin {
    /** Last used tab (LUT) is shown after an idle return. */
    fun onLutShownAfterIdle() {}

    /** User submitted a search or chat query via the omnibar. */
    fun onInputSubmitted() {}

    /** User opened the tab switcher. */
    fun onTabSwitcherSelected() {}

    /** User pressed the back button. */
    fun onBackPressed() {}

    /** User touched the WebView. */
    fun onWebViewEngaged() {}
}
