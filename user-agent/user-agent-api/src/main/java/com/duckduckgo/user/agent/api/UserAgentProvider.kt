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

package com.duckduckgo.user.agent.api

import android.webkit.WebSettings

interface UserAgentProvider {
    /**
     * Provides the user agent based on a specific URL and if the website should be displayed in desktop mode or not
     * @param url where the ua will be set
     * @param isDesktop boolean to know if the website should be displayed in desktop mode or not
     * @return a string with the user agent
     */
    fun userAgent(url: String? = null, isDesktop: Boolean = false): String

    /**
     * Sets the correct Sec-CH-UA hint header for our browser
     */
    fun setHintHeader(settings: WebSettings)
}

/**
 * A plugin to intercept the user agent, useful when we want to re-write the user agent for specific cases. Only used internally so far.
 */
interface UserAgentInterceptor {
    /**
     * @param userAgent the user agent initially set
     * @return a string with the new user agent
     */
    fun intercept(userAgent: String): String
}
