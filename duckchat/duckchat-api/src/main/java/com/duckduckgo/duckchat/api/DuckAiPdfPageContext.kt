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

/** PDF page-context metadata for the browser's contextual chat entry point. */
interface DuckAiPdfPageContext {
    fun isEnabled(): Boolean

    /**
     * Returns serialized page-context metadata without PDF bytes, or null when PDF context is
     * disabled, no PDF is displayed, or the request became stale.
     * Unavailable and oversized documents return metadata marked as non-attachable.
     */
    suspend fun getMetadata(tabId: String): String?
}
