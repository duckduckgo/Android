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

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Manages the inline PDF rendering pipeline: eligibility checks, download with
 * validation, and cache lifecycle.
 */
interface InlinePdfHandler {

    /**
     * Determines whether a download response should be rendered as an inline PDF
     * inside the browser tab instead of triggering a standard file download.
     *
     * Checks SDK level (>= Android 12), MIME type or `.pdf` URL extension, and
     * rejects `Content-Disposition: attachment` responses.
     *
     * @param url the URL of the PDF document
     * @param contentDisposition the value of the `Content-Disposition` header, if present
     * @param mimeType the MIME type of the response, if provided by the server
     * @return true if the response should be rendered inline, false to trigger a download
     */
    fun shouldRenderPdfInline(url: String, contentDisposition: String?, mimeType: String): Boolean

    /**
     * Downloads the PDF at [url] into internal cache for inline rendering.
     *
     * Forwards WebView cookies for authenticated downloads, validates the file
     * starts with `%PDF-` magic bytes, and returns a `file://` URI on success.
     *
     * Returns `null` if the server returns an error or the downloaded file is not a
     * valid PDF. The feature flag and SDK gate live in [shouldRenderPdfInline] so
     * callers shouldn't reach this method when the feature is off.
     *
     * Cancellation-safe: if the calling coroutine is cancelled (e.g. the user
     * navigates away), the in-flight HTTP request is aborted and any partial
     * file is deleted.
     *
     * @param url the URL of the PDF to download
     */
    suspend fun downloadToCache(url: String): Uri?

    /**
     * Extracts a sanitized filename from the PDF [url]'s last path segment.
     *
     * @param url the URL of the PDF document
     */
    fun extractFileName(url: String): String
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealInlinePdfHandler @Inject constructor(
    private val context: Context,
    @PdfOkHttpClient private val okHttpClient: OkHttpClient,
    private val cookieManagerProvider: CookieManagerProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : InlinePdfHandler {

    override fun shouldRenderPdfInline(url: String, contentDisposition: String?, mimeType: String): Boolean {
        if (!androidBrowserConfigFeature.pdfViewer().isEnabled()) return false
        if (Build.VERSION.SDK_INT < 31) return false
        // Use lastPathSegment so query strings and fragments don't break the .pdf check
        // (e.g. signed URLs like https://cdn.example.com/report.pdf?auth=token).
        val pathEndsInPdf = url.toUri().lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true
        if (mimeType != "application/pdf" && !pathEndsInPdf) return false
        if (contentDisposition != null && contentDisposition.trim().startsWith("attachment", ignoreCase = true)) return false
        return true
    }

    private val cacheDir: File
        get() = File(context.cacheDir, PDF_CACHE_DIR).also { it.mkdirs() }

    override suspend fun downloadToCache(url: String): Uri? = withContext(dispatcherProvider.io()) {
        val fileName = extractFileName(url)
        // Prefix the cache filename with the URL hash so two URLs sharing a last path segment
        // (e.g. report.pdf at site A and site B) don't collide and serve stale content.
        val targetFile = File(cacheDir, "${url.hashCode()}-$fileName")
        try {
            if (targetFile.exists() && hasPdfMagicBytes(targetFile)) {
                return@withContext Uri.fromFile(targetFile)
            }

            val requestBuilder = Request.Builder().url(url)

            val cookie = cookieManagerProvider.get()?.getCookie(url)
            if (cookie != null) {
                requestBuilder.addHeader("Cookie", cookie)
            }

            executeRequestCancellably(okHttpClient.newCall(requestBuilder.build())).use { response ->
                if (!response.isSuccessful) {
                    logcat { "PDF download failed: HTTP ${response.code}" }
                    return@withContext null
                }

                response.body?.byteStream()?.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } >= 0) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                } ?: run {
                    logcat { "PDF download failed: empty response body" }
                    return@withContext null
                }
            }

            coroutineContext.ensureActive()

            if (!hasPdfMagicBytes(targetFile)) {
                logcat { "PDF download failed: file does not start with %PDF magic bytes" }
                targetFile.delete()
                return@withContext null
            }

            Uri.fromFile(targetFile)
        } catch (e: CancellationException) {
            logcat { "PDF download cancelled, cleaning up partial file" }
            targetFile.delete()
            throw e
        } catch (e: IOException) {
            logcat { "PDF download failed: ${e.message}" }
            null
        }
    }

    /**
     * Executes an OkHttp [call] in a way that respects coroutine cancellation.
     * When the coroutine is cancelled, the underlying HTTP call is also cancelled.
     */
    private suspend fun executeRequestCancellably(call: Call): Response {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(response))
                    } else {
                        // Coroutine was cancelled in the narrow window between the response
                        // landing and this callback firing — close the body so OkHttp can
                        // return the underlying connection to the pool.
                        response.close()
                    }
                }
            })
        }
    }

    private fun hasPdfMagicBytes(file: File): Boolean {
        if (file.length() < PDF_MAGIC_BYTES.size) return false
        val header = ByteArray(PDF_MAGIC_BYTES.size)
        file.inputStream().use { it.read(header) }
        return header.contentEquals(PDF_MAGIC_BYTES)
    }

    override fun extractFileName(url: String): String {
        val path = url.toUri().lastPathSegment ?: "document.pdf"
        val sanitized = path.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (sanitized.endsWith(".pdf", ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized.pdf"
        }
    }

    companion object {
        private const val PDF_CACHE_DIR = "pdf_cache"
        private val PDF_MAGIC_BYTES = "%PDF-".toByteArray()
    }
}
