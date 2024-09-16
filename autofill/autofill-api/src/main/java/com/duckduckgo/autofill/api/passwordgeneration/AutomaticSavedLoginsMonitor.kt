/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.api.passwordgeneration

/**
 * When password generation happens, we automatically create a login.
 * This login might be later updated with more information when the form is submitted.
 *
 * We need a way to monitor if a login was automatically created, for a specific tab, so we get data about it when form submitted.
 *
 * By design, an automatically saved login is only monitored for the current page; when a navigation event happens it will be cleared.
 */
interface AutomaticSavedLoginsMonitor {

    /**
     * Retrieves the automatically saved login ID for the current tab, if any.
     * @return the login ID, or null if no login was automatically saved for the current tab.
     */
    fun getAutoSavedLoginId(tabId: String?): Long?

    /**
     * Sets the automatically saved login ID for the current tab.
     */
    fun setAutoSavedLoginId(value: Long, tabId: String?)

    /**
     * Clears the automatically saved login ID for the current tab.
     */
    fun clearAutoSavedLoginId(tabId: String?)
}
