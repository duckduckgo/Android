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
import com.duckduckgo.browser.api.pdf.PdfPageContext
import com.duckduckgo.browser.api.pdf.PdfPageContext.Status
import com.duckduckgo.browser.api.pdf.PdfPageContextProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPdfPageContextProvider @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : PdfPageContextProvider {
    private class Document(val uri: Uri?, val title: String, val url: String)
    private val documents = ConcurrentHashMap<String, Document>()

    fun show(tabId: String, uri: Uri?, title: String, url: String) {
        val old = documents[tabId]
        if (old?.uri == uri && old?.title == title && old?.url == url) return
        documents[tabId] = Document(uri, title, url)
    }

    fun hide(tabId: String) {
        documents.remove(tabId)
    }

    override suspend fun get(tabId: String, includeData: Boolean): PdfPageContext? {
        val document = documents[tabId] ?: return null
        val result = withContext(dispatchers.io()) {
            read(document, includeData)
        }
        coroutineContext.ensureActive()
        return if (documents[tabId] === document) result else result.copy(status = Status.STALE, data = null)
    }

    private suspend fun read(document: Document, includeData: Boolean): PdfPageContext {
        val metadata = PdfPageContext(document.title, document.url, Status.UNAVAILABLE)
        val path = document.uri?.takeIf { it.scheme == "file" }?.path ?: return metadata
        try {
            val file = File(path)
            if (!file.isFile || file.length() == 0L) return metadata
            if (file.length() > MAX_BYTES) return metadata.copy(status = Status.TOO_LARGE)
            if (!includeData) return metadata.copy(status = Status.AVAILABLE)
            file.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BYTES) return metadata.copy(status = Status.TOO_LARGE)
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                if (bytes.size < 5 || !bytes.copyOfRange(0, 5).contentEquals(PDF_MAGIC)) return metadata
                coroutineContext.ensureActive()
                return metadata.copy(status = Status.AVAILABLE, data = Base64.getEncoder().encodeToString(bytes))
            }
        } catch (_: IOException) {
            return metadata
        } catch (_: SecurityException) {
            return metadata
        }
    }

    companion object {
        private const val MAX_BYTES = 30 * 1024 * 1024
        private val PDF_MAGIC = "%PDF-".toByteArray()
    }
}
