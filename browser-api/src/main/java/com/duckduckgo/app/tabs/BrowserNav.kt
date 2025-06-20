/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.tabs

import android.content.Context
import android.content.Intent

/**
 * Public interface to provide navigation Intents related to browser screen
 */
interface BrowserNav {
    // opens url on a new tab
    fun openInNewTab(context: Context, url: String): Intent
    fun openInCurrentTab(context: Context, url: String): Intent
    fun openDuckChat(context: Context, hasSessionActive: Boolean = false, duckChatUrl: String): Intent
    fun closeDuckChat(context: Context): Intent
}
