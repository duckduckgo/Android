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

package com.duckduckgo.app.tabs.model

import com.duckduckgo.browsermode.api.AggregateBrowserModeProvider

/**
 * Observes tabs across one or more [com.duckduckgo.browsermode.api.BrowserMode]s. Callers pass the
 * set of modes they want tabs from — a single mode for a per-mode view, or omit the argument for a
 * cross-mode view.
 *
 * For mutating tab state, use the mode-qualified [TabRepository] directly.
 */
interface AggregateTabProvider : AggregateBrowserModeProvider<List<TabEntity>>
