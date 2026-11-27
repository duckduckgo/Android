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

package com.duckduckgo.duckchat.api

/** Receives browser interactions used to attribute Duck.ai entries and exits. */
interface DuckAiEntryCallback {
    /** Defers app-open attribution until the launch intent has been handled. */
    fun onLaunchIntentReceived(opensDuckAi: Boolean = false)

    /** Allows app-open attribution after an intent has had the opportunity to report an explicit entry. */
    fun onLaunchIntentHandled()

    /** Records a tab swipe to [tabId]; [url] determines whether the destination is Duck.ai. */
    fun onTabSwiped(tabId: String, url: String?)

    /** Records a search intent in [tabId]; the entry ends only after navigation away from Duck.ai. */
    fun onSearchStarted(tabId: String)
}
