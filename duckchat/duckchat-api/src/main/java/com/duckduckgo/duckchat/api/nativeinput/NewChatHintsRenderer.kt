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

package com.duckduckgo.duckchat.api.nativeinput

import android.content.Context
import android.view.View

/**
 * Creates the New Chat Hints shown above the native input on a full-screen Duck.ai New Chat.
 * The view decides its own visibility; the host only places it.
 */
interface NewChatHintsRenderer {
    /**
     * Returns null when the feature is disabled, so the host adds and observes nothing.
     * [context] should be the native input card's context so themed colours (e.g. Fire Tabs) apply.
     */
    suspend fun create(context: Context, tabId: String): View?
}
