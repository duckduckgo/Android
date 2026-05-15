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

package com.duckduckgo.app.browser.mode

import com.duckduckgo.browser.api.mode.BrowserMode

/**
 * Identifies where a BrowserActivity launch originated. Drives the decision of whether the
 * incoming intent must force a switch to [BrowserMode.REGULAR] before being processed.
 */
sealed interface BrowserLaunchSource {
    val requiresRegularMode: Boolean
}

/** Launch originated outside the active browser session (widgets, shortcuts, notifications, etc) */
sealed interface ExternalLaunchSource : BrowserLaunchSource {
    override val requiresRegularMode: Boolean get() = true
}

/** Launch originated inside the running app (in-app navigation, onboarding, fire restart) */
sealed interface InternalLaunchSource : BrowserLaunchSource {
    override val requiresRegularMode: Boolean get() = false
}

// External entry points

/** VIEW / SEND / NDEF intents routed through `IntentDispatcherActivity`. */
data object ExternalUrl : ExternalLaunchSource

/** `ACTION_PROCESS_TEXT` or `ACTION_WEB_SEARCH` via `SelectedTextSearchActivity`. */
data object SelectedTextSearch : ExternalLaunchSource

/** Privacy protection notification tap. */
data object PrivacyNotification : ExternalLaunchSource

/** ACTION_ASSIST, NEW_SEARCH, or widget-routed launches through `SystemSearchActivity`. */
data object SystemSearchExternal : ExternalLaunchSource

/** Favorites widget tap. */
data object FavoritesWidget : ExternalLaunchSource

/** Search widget Duck.ai button. */
data object SearchWidgetDuckAi : ExternalLaunchSource

/** App long-press shortcut: "New Tab". */
data object AppShortcutNewTab : ExternalLaunchSource

/** App long-press shortcut: "Bookmarks". */
data object AppShortcutBookmarks : ExternalLaunchSource

/** App long-press shortcut: "Duck.ai". */
data object AppShortcutDuckAi : ExternalLaunchSource

/** User-created home-screen shortcut for a specific website. */
data object PinnedPageShortcut : ExternalLaunchSource

/** Duck.ai shortcut from the widget picker. */
data object DuckAiPinShortcut : ExternalLaunchSource

// Internal entry points

/** App icon launcher tap. */
data object AppLauncher : InternalLaunchSource

/** First-run completion forwarding to BrowserActivity. */
data object Onboarding : InternalLaunchSource

/** Process restart after fire data-clear. */
data object FireRestart : InternalLaunchSource

/**
 * In-app navigation: settings, bookmarks, autofill, NTP, in-app Duck.ai button, tab switcher, etc.
 */
data object InAppNavigation : InternalLaunchSource
