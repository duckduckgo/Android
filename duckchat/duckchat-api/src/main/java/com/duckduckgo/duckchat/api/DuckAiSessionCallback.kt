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

/**
 * Receives lifecycle and interaction events for visits to a full Duck.ai tab.
 */
interface DuckAiSessionCallback {
    /**
     * Called once per app-open. [tabId] and [url] are supplied only when the resolved landing is Duck.ai.
     */
    fun onLaunchLandingResolved(tabId: String?, url: String?)

    /**
     * Called whenever [tabId] finishes loading [url] and it is a full Duck.ai page for the currently active tab.
     */
    fun onDuckAiPageVisible(tabId: String, url: String)

    /**
     * Records the selected-tab state for [tabId] and [url].
     */
    fun onSelectedTabChanged(tabId: String?, url: String?)

    /**
     * Records that [tabId] may leave Duck.ai for [trigger].
     */
    fun onExitIntent(tabId: String, trigger: DuckAiSessionExitTrigger)

    /** Records that a prompt was submitted from the active Duck.ai session in [tabId]. */
    fun onPromptSubmitted(tabId: String)

    /** Records that a new chat was created from the active Duck.ai session in [tabId]. */
    fun onNewChatCreated(tabId: String)
}

enum class DuckAiSessionExitTrigger {
    BACK_OR_CLOSE,
    TAB_SWITCHED,
    NEW_TAB_OPENED,
    FIRE_TAB_OPENED,
    OTHER_NAVIGATION,
}
