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

package com.duckduckgo.browser.api.pdf

/** Reads the cached document currently displayed by a tab without fetching it again. */
interface PdfPageContextProvider {
    /**
     * Null means no PDF is displayed. Unavailable PDFs must not fall back to the hidden page.
     * [includeData] permits a bounded base64 read; false returns metadata without reading content.
     * STALE results must be discarded because the tab changed during the read. Cancellation propagates.
     */
    suspend fun get(tabId: String, includeData: Boolean): PdfPageContext?
}

/** Document metadata and optional base64 bytes. Never persist or log [data]. */
data class PdfPageContext(
    val title: String,
    val url: String,
    val status: Status,
    val data: String? = null,
) {
    enum class Status { AVAILABLE, TOO_LARGE, UNAVAILABLE, STALE }
}
