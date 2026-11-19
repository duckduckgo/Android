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

package com.duckduckgo.app.browser.pdf

import android.net.Uri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.browser.api.pdf.PdfContentResult
import com.duckduckgo.browser.api.pdf.PdfPageContext
import com.duckduckgo.browser.api.pdf.PdfPageContext.Status
import com.duckduckgo.browser.api.pdf.PdfPageContextProvider
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PdfPageContextProvider>())
@ContributesBinding(AppScope::class, binding = binding<PdfPageContextRegistry>())
class RealPdfPageContextProvider @Inject constructor(
    private val dispatchers: DispatcherProvider,
    @RegularMode private val regularTabs: TabsDao,
    @FireMode private val fireTabs: TabsDao,
    @AppCoroutineScope private val scope: CoroutineScope,
) : PdfPageContextProvider, PdfPageContextRegistry {
    private class Document(val tabId: String, val uri: Uri?, val title: String, val url: String) : PdfPageContext.Snapshot
    private val documents = ConcurrentHashMap<String, Document>()

    init {
        scope.launch(dispatchers.io()) {
            merge(
                regularTabs.flowAllTabIds().map { it.toSet() }.distinctUntilChanged(),
                fireTabs.flowAllTabIds().map { it.toSet() }.distinctUntilChanged(),
            ).collect {
                documents.values.toList().forEach { discardRemovedTab(it) }
            }
        }
    }

    private fun discardRemovedTab(document: Document): Boolean {
        val exists = regularTabs.tab(document.tabId) != null || fireTabs.tab(document.tabId) != null
        if (!exists) documents.remove(document.tabId, document)
        return exists
    }

    override fun show(tabId: String, uri: Uri?, title: String, url: String) {
        val document = Document(tabId, uri, title, url)
        documents[tabId] = document
        scope.launch(dispatchers.io()) { discardRemovedTab(document) }
    }

    override fun hide(tabId: String) {
        documents.remove(tabId)
    }

    override suspend fun getMetadata(tabId: String): PdfPageContext? {
        val document = documents[tabId] ?: return null
        val status = withContext(dispatchers.io()) {
            if (!discardRemovedTab(document)) return@withContext Status.STALE
            val inspected = inspect(document)
            if (discardRemovedTab(document)) inspected else Status.STALE
        }
        coroutineContext.ensureActive()
        return PdfPageContext(document.title, document.url, if (isCurrent(document)) status else Status.STALE, document)
    }

    override suspend fun read(snapshot: PdfPageContext.Snapshot): PdfContentResult {
        val document = snapshot as? Document ?: return PdfContentResult.Stale
        if (!isCurrent(document)) return PdfContentResult.Stale
        val result = withContext(dispatchers.io()) {
            if (!discardRemovedTab(document) || !isCurrent(document)) return@withContext PdfContentResult.Stale
            val content = readDocument(document)
            if (discardRemovedTab(document)) content else PdfContentResult.Stale
        }
        coroutineContext.ensureActive()
        return if (isCurrent(document)) result else PdfContentResult.Stale
    }

    private fun isCurrent(document: Document): Boolean = documents[document.tabId] === document

    private fun file(document: Document): File? = document.uri?.takeIf { it.scheme == "file" }?.path?.let(::File)

    private suspend fun inspect(document: Document): Status {
        try {
            coroutineContext.ensureActive()
            val file = file(document) ?: return Status.UNAVAILABLE
            if (!file.isFile || file.length() < PDF_MAGIC.size) return Status.UNAVAILABLE
            if (file.length() > MAX_BYTES) return Status.TOO_LARGE
            file.inputStream().use { input ->
                for (byte in PDF_MAGIC) if (input.read() != byte.toInt()) return Status.UNAVAILABLE
            }
            return Status.AVAILABLE
        } catch (_: IOException) {
            return Status.UNAVAILABLE
        } catch (_: SecurityException) {
            return Status.UNAVAILABLE
        }
    }

    private suspend fun readDocument(document: Document): PdfContentResult {
        try {
            val file = file(document) ?: return PdfContentResult.Unavailable
            val size = file.length()
            if (!file.isFile || size < PDF_MAGIC.size) return PdfContentResult.Unavailable
            if (size > MAX_BYTES) return PdfContentResult.TooLarge
            file.inputStream().use { input ->
                val bytes = ByteArray(size.toInt())
                var offset = 0
                while (offset < bytes.size) {
                    coroutineContext.ensureActive()
                    val count = input.read(bytes, offset, minOf(DEFAULT_BUFFER_SIZE, bytes.size - offset))
                    if (count < 0) return PdfContentResult.Unavailable
                    offset += count
                }
                if (input.read() != -1) return if (file.length() > MAX_BYTES) PdfContentResult.TooLarge else PdfContentResult.Unavailable
                if (PDF_MAGIC.indices.any { bytes[it] != PDF_MAGIC[it] }) return PdfContentResult.Unavailable
                coroutineContext.ensureActive()
                return PdfContentResult.Success(bytes)
            }
        } catch (_: IOException) {
            return PdfContentResult.Unavailable
        } catch (_: SecurityException) {
            return PdfContentResult.Unavailable
        }
    }

    companion object {
        private const val MAX_BYTES = 30 * 1024 * 1024
        private val PDF_MAGIC = "%PDF-".toByteArray()
    }
}
