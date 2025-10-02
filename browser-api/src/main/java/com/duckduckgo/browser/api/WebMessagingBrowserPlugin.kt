/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.browser.api

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.js.messaging.api.WebMessaging

/**
 * Interface to provide implementations of [WebMessaging] to the browser, through
 * [PluginPoint]<[WebMessaging]>
 */
interface WebMessagingBrowserPlugin {
    /**
     * Provides an implementation of [WebMessaging] to be used by the browser.
     * @return an instance of [WebMessaging]
     */
    fun webMessaging(): WebMessaging
}
