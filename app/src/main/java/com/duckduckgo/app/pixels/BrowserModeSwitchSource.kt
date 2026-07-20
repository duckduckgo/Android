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

package com.duckduckgo.app.pixels

/** Origin of a [AppPixelName.BROWSER_MODE_SWITCHED] sent as its `source` parameter. */
enum class BrowserModeSwitchSource(val value: String) {
    /** The mode toggle in the tab switcher (tap or drag). */
    TAB_SWITCHER_TOGGLE("tab_switcher_toggle"),

    /** Fire Tabs promo CTA deeplinking into the Fire tab switcher. */
    PROMOTION("promotion"),

    /** A new tab opened in the other mode (chat menu New Tab / New Fire Tab, long-press Open in Fire Tab). */
    NEW_TAB("new_tab"),

    /** The escape hatch returning to a tab in the other mode. */
    ESCAPE_HATCH("escape_hatch"),

    /** An external launch (link, widget, notification, assist) that must open in Regular mode. */
    EXTERNAL_LAUNCH("external_launch"),

    /** Up/back from the empty Fire tab switcher, exiting to Regular mode. */
    TAB_SWITCHER_EXIT("tab_switcher_exit"),
}
