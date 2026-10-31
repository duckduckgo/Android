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

package com.duckduckgo.duckchat.impl.contextual

import android.os.SystemClock
import com.duckduckgo.browser.api.pdf.PdfPageContext.Status
import com.duckduckgo.browser.api.pdf.PdfPageContextProvider
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.Category
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.ContextType
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.Reason
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.Trigger
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class PdfPageContextResolver @Inject constructor(
    private val feature: DuckChatFeature,
    private val provider: PdfPageContextProvider,
    private val pixels: PageContextExtractionPixels,
) {
    fun isEnabled(): Boolean = feature.pdfPageContext().isEnabled()

    suspend fun resolve(
        tabId: String,
        context: String?,
        includeData: Boolean,
        trigger: Trigger = Trigger.TAB_CONTENT,
    ): String? {
        if (!isEnabled()) return context
        val started = SystemClock.elapsedRealtime()
        val document = provider.get(tabId, includeData)
            ?: return context?.takeUnless { isPdf(it) }
        if (document.status == Status.STALE) return null
        val previous = context?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (previous?.optString("mimeType") == PDF_MIME_TYPE && previous.optString("url") != document.url) return null
        when (document.status) {
            Status.TOO_LARGE -> pixels.prevented(Category.DOCUMENT_TOO_LARGE, trigger)
            Status.UNAVAILABLE -> if (includeData) {
                pixels.failed(
                    Reason.DOCUMENT_UNAVAILABLE,
                    trigger,
                    ContextType.PDF,
                    SystemClock.elapsedRealtime() - started,
                )
            }
            Status.AVAILABLE -> if (document.data != null) pixels.success(ContextType.PDF)
            Status.STALE -> return null
        }
        return JSONObject().apply {
            put("title", document.title)
            put("url", document.url)
            put("content", "")
            put("favicon", JSONArray())
            put("truncated", false)
            put("fullContentLength", 0)
            put("mimeType", PDF_MIME_TYPE)
            put("attachable", document.status == Status.AVAILABLE)
            put("attached", document.data != null)
            document.data?.let { put("data", it) }
        }.toString()
    }

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        fun isPdf(context: String): Boolean = runCatching { JSONObject(context).optString("mimeType") == PDF_MIME_TYPE }.getOrDefault(false)
    }
}
