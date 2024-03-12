/*
 * Copyright (c) 2024 DuckDuckGo
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

interface ClientBrandHintProvider {

    /**
     * Sets the client hint header SEC-CH-UA based on a specific URL and the fact that it can be an exception
     * @param settings [WebSettings] where the agent metadata will be set
     * @param url where the client hint will be set
     */
    fun setOn(settings: WebSettings?, documentUrl: String)

    /**
     * Sets the default client hint header SEC-CH-UA
     * @param settings [WebSettings] where the agent metadata will be set
     */
    fun setDefault(settings: WebSettings)

    /**
     * Checks if the url passed as parameter will force a change of branding
     * @param url where the client hint will be set
     * @return true if the branding should change
     */
    fun shouldChangeBranding(documentUrl: String): Boolean
}
