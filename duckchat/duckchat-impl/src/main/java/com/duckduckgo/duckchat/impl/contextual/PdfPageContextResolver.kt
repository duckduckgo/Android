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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.pdf.PdfContentResult
import com.duckduckgo.browser.api.pdf.PdfPageContext
import com.duckduckgo.browser.api.pdf.PdfPageContext.Status
import com.duckduckgo.browser.api.pdf.PdfPageContextProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiPdfMetadata
import com.duckduckgo.duckchat.api.DuckAiPdfPageContext
import com.duckduckgo.duckchat.impl.DuckChatConstants.PDF_MIME_TYPE
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.Category
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.ContextType
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.Reason
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.IdentityHashMap
import javax.inject.Inject
import com.duckduckgo.duckchat.api.PageContextTrigger as Trigger

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PdfPageContextResolver @Inject constructor(
    private val feature: DuckChatFeature,
    private val provider: PdfPageContextProvider,
    private val pixels: PageContextExtractionPixels,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val scope: CoroutineScope,
) : DuckAiPdfPageContext {
    private sealed interface EncodedContent {
        class Success(val data: String) : EncodedContent
        data object TooLarge : EncodedContent
        data object Unavailable : EncodedContent
        data object Stale : EncodedContent
    }

    private class Read(val result: Deferred<EncodedContent>, var waiters: Int = 0)
    private val reads = IdentityHashMap<PdfPageContext.Snapshot, Read>()

    private suspend fun <T> withContent(snapshot: PdfPageContext.Snapshot, consume: suspend (EncodedContent) -> T): T {
        val read = synchronized(reads) {
            reads.getOrPut(snapshot) {
                Read(
                    scope.async(dispatchers.io(), start = CoroutineStart.LAZY) {
                        when (val content = provider.read(snapshot)) {
                            is PdfContentResult.Success -> EncodedContent.Success(Base64.getEncoder().encodeToString(content.bytes))
                            PdfContentResult.TooLarge -> EncodedContent.TooLarge
                            PdfContentResult.Unavailable -> EncodedContent.Unavailable
                            PdfContentResult.Stale -> EncodedContent.Stale
                        }
                    },
                )
            }.also { it.waiters++ }
        }
        try {
            return consume(read.result.await())
        } finally {
            synchronized(reads) {
                if (--read.waiters == 0) {
                    reads.remove(snapshot)
                    read.result.cancel()
                }
            }
        }
    }

    sealed interface Result {
        fun toJson(): JSONObject
        fun serialize(): String = toJson().toString()

        data class Pdf(val document: DuckAiPdfMetadata, val data: String? = null) : Result {
            override fun toString(): String = "PdfPageContextResolver.Result.Pdf"
            override fun toJson(): JSONObject = JSONObject().apply {
                put("title", document.title)
                put("url", document.url)
                put("content", "")
                put("favicon", JSONArray())
                put("truncated", false)
                put("fullContentLength", 0)
                put("mimeType", PDF_MIME_TYPE)
                put("attachable", document.availability == DuckAiPdfMetadata.Availability.AVAILABLE)
                put("attached", data != null)
                data?.let { put("data", it) }
            }
        }

        data class Page(val serialized: String) : Result {
            override fun toString(): String = "PdfPageContextResolver.Result.Page"
            override fun toJson(): JSONObject = JSONObject(serialized)
            override fun serialize(): String = serialized
        }
    }

    internal fun isEnabled(): Boolean = feature.pdfPageContext().isEnabled()

    override suspend fun getMetadata(tabId: String): DuckAiPdfMetadata? {
        if (!isEnabled()) return null
        return provider.getMetadata(tabId)?.toMetadata()
    }

    private fun PdfPageContext.toMetadata() = DuckAiPdfMetadata(
        title,
        url,
        when (status) {
            Status.AVAILABLE -> DuckAiPdfMetadata.Availability.AVAILABLE
            Status.TOO_LARGE -> DuckAiPdfMetadata.Availability.TOO_LARGE
            Status.UNAVAILABLE -> DuckAiPdfMetadata.Availability.UNAVAILABLE
            Status.STALE -> DuckAiPdfMetadata.Availability.STALE
        },
    )

    suspend fun resolve(
        tabId: String,
        context: String?,
        includeData: Boolean,
        trigger: Trigger = Trigger.TAB_CONTENT,
    ): Result? {
        if (!isEnabled()) return context?.let { Result.Page(it) }
        val started = SystemClock.elapsedRealtime()
        val document = provider.getMetadata(tabId)
            ?: return context?.takeUnless { isPdf(it) }?.let { Result.Page(it) }
        if (document.status == Status.STALE) return null
        val previous = context?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (previous?.optString("mimeType") == PDF_MIME_TYPE && previous.optString("url") != document.url) return null
        if (!includeData) return Result.Pdf(document.toMetadata())
        return withContent(document.snapshot) { content ->
            val current = provider.getMetadata(tabId)
            if (current?.snapshot !== document.snapshot || current.status == Status.STALE) return@withContent null
            var metadata = document.toMetadata()
            var data: String? = null
            when (content) {
                is EncodedContent.Success -> {
                    data = content.data
                    metadata = metadata.copy(availability = DuckAiPdfMetadata.Availability.AVAILABLE)
                    pixels.success(ContextType.PDF)
                }
                EncodedContent.TooLarge -> {
                    metadata = metadata.copy(availability = DuckAiPdfMetadata.Availability.TOO_LARGE)
                    pixels.prevented(Category.DOCUMENT_TOO_LARGE, trigger)
                }
                EncodedContent.Unavailable -> {
                    metadata = metadata.copy(availability = DuckAiPdfMetadata.Availability.UNAVAILABLE)
                    pixels.failed(Reason.DOCUMENT_UNAVAILABLE, trigger, ContextType.PDF, SystemClock.elapsedRealtime() - started)
                }
                EncodedContent.Stale -> return@withContent null
            }
            Result.Pdf(metadata, data)
        }
    }

    companion object {
        fun isPdf(context: String): Boolean = runCatching { JSONObject(context).optString("mimeType") == PDF_MIME_TYPE }.getOrDefault(false)
    }
}
