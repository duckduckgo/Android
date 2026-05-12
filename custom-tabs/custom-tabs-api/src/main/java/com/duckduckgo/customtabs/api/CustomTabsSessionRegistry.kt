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

package com.duckduckgo.customtabs.api

import androidx.browser.customtabs.CustomTabsSessionToken

/**
 * Tracks verified Custom Tabs sessions (token -> calling package), populated from
 * DuckDuckGoCustomTabService.newSession() and read by IntentDispatcherViewModel
 * to support handleAppLink's trusted-caller carve-out.
 */
interface CustomTabsSessionRegistry {

    /** Records a verified (session token -> calling package) binding. Called from newSession(). */
    fun recordSession(sessionToken: CustomTabsSessionToken, packageName: String)

    /** Returns the verified calling package for the session, or null if no verified binding exists. */
    fun lookupClientPackage(sessionToken: CustomTabsSessionToken): String?

    /** Removes the binding for a session, e.g. on cleanUpSession() when the client disconnects. */
    fun clearSession(sessionToken: CustomTabsSessionToken)
}
