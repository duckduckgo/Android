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

package com.duckduckgo.request.filterer.api

import android.webkit.WebResourceRequest

/** Public interface for the Request Filterer feature */
interface RequestFilterer {
    /**
     * This method takes a [request] and a [documentUrl] to calculate if the request should be filtered out or not.
     * @return `true` if the request should be filtered or `false` if it shouldn't
     */
    fun shouldFilterOutRequest(request: WebResourceRequest, documentUrl: String?): Boolean

    /**
     * This method takes a [url] and registers it internally. This method must be used before using `shouldFilterOutRequest`
     * to correctly register the different page created events.
     */
    fun registerOnPageCreated(url: String)
}
