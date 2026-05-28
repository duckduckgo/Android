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

package com.duckduckgo.browsermode.api

import androidx.webkit.Profile

/**
 * Stable mapping from a [BrowserMode] to its WebView profile name.
 *
 * [REGULAR][BrowserMode.REGULAR] shares the default WebView profile;
 * [FIRE][BrowserMode.FIRE] gets its own isolated profile.
 */
val BrowserMode.profileName: String
    get() = when (this) {
        BrowserMode.REGULAR -> Profile.DEFAULT_PROFILE_NAME
        BrowserMode.FIRE -> "Fire"
    }
