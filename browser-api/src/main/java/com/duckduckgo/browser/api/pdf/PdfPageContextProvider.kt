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

/** Reads the viewer's existing cache without fetching or persisting another document copy. */
interface PdfPageContextProvider {
    /** Null means no PDF. Other results must not fall back to the hidden web page. Cancellation propagates. */
    suspend fun getMetadata(tabId: String): PdfPageContext?

    /** Reads the inspected document only. Consumers own encoding and must exclude content from logs and page-context persistence. */
    suspend fun read(snapshot: PdfPageContext.Snapshot): PdfContentResult
}

/** Metadata contains no attachment bytes. Title and URL are sensitive and must not be logged. */
data class PdfPageContext(
    val title: String,
    val url: String,
    val status: Status,
    val snapshot: Snapshot,
) {
    /** Opaque local document identity. Never serialize or persist this handle. */
    interface Snapshot
    enum class Status { AVAILABLE, TOO_LARGE, UNAVAILABLE, STALE }
    override fun toString(): String = "PdfPageContext(status=$status)"
}

sealed interface PdfContentResult {
    /** Complete validated bytes, retained only for attachment delivery. Existing chat storage has its own retention policy. */
    class Success(val bytes: ByteArray) : PdfContentResult {
        override fun toString(): String = "PdfContentResult.Success"
    }
    data object TooLarge : PdfContentResult
    data object Unavailable : PdfContentResult
    data object Stale : PdfContentResult
}
