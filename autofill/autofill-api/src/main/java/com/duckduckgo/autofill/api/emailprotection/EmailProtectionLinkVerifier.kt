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

package com.duckduckgo.autofill.api.emailprotection

/**
 * Determines if a link should be consumed by the in-context Email Protection feature.
 *
 * If the link should be consumed, it will be delegated to the in-context view.
 * If the link should not be consumed, it should open as normal in the browser.
 *
 */
interface EmailProtectionLinkVerifier {

    /**
     * Determines if a link should be consumed by the in-context Email Protection feature or opened as a normal URL in the browser.
     *
     * @param url The url which will be checked to determine if a verification link or not
     * @param inContextViewAlreadyShowing Whether the in-context view is already showing. If it is not showing, then
     * the link should not be consumed in-context.
     */
    fun shouldDelegateToInContextView(
        url: String?,
        inContextViewAlreadyShowing: Boolean?,
    ): Boolean
}
