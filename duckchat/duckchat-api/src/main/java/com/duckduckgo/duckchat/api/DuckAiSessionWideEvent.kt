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
 * Covers every visit to a full Duck.ai tab, from becoming visible to a confirmed exit, as one wide event.
 */
interface DuckAiSessionWideEvent {
    /**
     * Called once per app-open. [tabId] and [url] are supplied only when the resolved landing is Duck.ai.
     * A repeated call for a tab that is already active is a no-op.
     */
    fun onLaunchLandingResolved(tabId: String?, url: String?)

    /**
     * Called whenever [tabId] finishes loading [url] and it's a full Duck.ai page, for the currently active tab.
     */
    fun onDuckAiPageVisible(tabId: String, url: String)

    /**
     * Records the selected-tab state for [tabId] and [url]
     */
    fun onSelectedTabChanged(tabId: String?, url: String?)

    /**
     * Records that [tabId] left Duck.ai for [trigger].
     */
    fun onExitIntent(tabId: String, trigger: ExitTrigger)

    /** Records that a prompt was submitted from the active Duck.ai session in [tabId]. */
    fun onPromptSubmitted(tabId: String)

    /** Records that a new chat was created from the active Duck.ai session in [tabId]. */
    fun onNewChatCreated(tabId: String)
}

enum class ExitTrigger(val value: String) {
    BACK_OR_CLOSE("back_or_close"),
    TAB_SWITCHED("tab_switched"),
    NEW_TAB_OPENED("new_tab_opened"),
    FIRE_TAB_OPENED("fire_tab_opened"),
    OTHER_NAVIGATION("other_navigation"),
}
