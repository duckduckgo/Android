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

package com.duckduckgo.duckchat.localserver.impl

import fi.iki.elonen.NanoHTTPD

/**
 * Handles HTTP requests for a specific path prefix on the duck-ai local server.
 *
 * Implementations are discovered via Dagger multibinding. To add a new endpoint,
 * implement this interface and annotate with [@ContributesMultibinding(AppScope::class)].
 */
interface DuckAiRequestHandler {
    /** Path prefix this handler owns, e.g. "/settings". Matched via [String.startsWith]. */
    val pathPrefix: String

    /**
     * Handle the request. Called only when [uri] starts with [pathPrefix].
     *
     * @param method the HTTP method
     * @param uri the full request URI
     * @param body the request body, or null if not a PUT/POST or body was empty
     */
    fun handle(method: NanoHTTPD.Method, uri: String, body: String?): NanoHTTPD.Response
}
