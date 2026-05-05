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
import androidx.annotation.VisibleForTesting
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
     * Decides how a download response should be handled for PDF rendering.
     *
     * - [PdfRenderDecision.Inline]: feature flag on, SDK >= Android 12, MIME or
     *   `.pdf` URL extension matches, and `Content-Disposition: attachment` not set.
     * - [PdfRenderDecision.Fallback]: same eligibility but the device is below
     *   Android 12 — caller should fall back to the standard file download.
     * - [PdfRenderDecision.NotApplicable]: response is not an inline-eligible PDF
     *   (or the feature flag is off).
     */
    fun classifyPdfRequest(url: String, contentDisposition: String?, mimeType: String): PdfRenderDecision

    /**
     * Downloads the PDF at [url] into internal cache for inline rendering.
     *
     * Forwards WebView cookies for authenticated downloads, validates the file
     * starts with `%PDF-` magic bytes, and returns [PdfDownloadResult.Success] on
     * success.
     *
     * Returns [PdfDownloadResult.Failure] with an error category when the network,
     * server, or file content prevents inline rendering. The feature flag and SDK
     * gate live in [classifyPdfRequest] so callers shouldn't reach this method when the
     * feature is off.
     *
     * Cancellation-safe: if the calling coroutine is cancelled (e.g. the user
     * navigates away), the in-flight HTTP request is aborted and any partial
     * file is deleted.
     *
     * @param url the URL of the PDF to download
     * @param forceRefresh when true, ignore any existing cache entry and re-fetch from the network.
     *   Used by the user-triggered refresh action so an updated server-side document replaces the
     *   stale cached copy.
     */
    suspend fun downloadToCache(url: String, forceRefresh: Boolean = false): PdfDownloadResult

    /**
     * Extracts a sanitized filename from the PDF [url]'s last path segment.
     *
     * @param url the URL of the PDF document
     */
    fun extractFileName(url: String): String
}

sealed class PdfRenderDecision {
    data object Inline : PdfRenderDecision()
    data object Fallback : PdfRenderDecision()
    data object NotApplicable : PdfRenderDecision()
}

sealed class PdfDownloadResult {
    data class Success(val uri: Uri) : PdfDownloadResult()
    data class Failure(val errorType: PdfErrorType) : PdfDownloadResult()
}

enum class PdfErrorType(val paramValue: String) {
    IO_ERROR("io_error"),
    SECURITY_ERROR("security_error"),
    UNKNOWN("unknown"),
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

    override fun classifyPdfRequest(url: String, contentDisposition: String?, mimeType: String): PdfRenderDecision {
        if (!androidBrowserConfigFeature.pdfViewer().isEnabled()) return PdfRenderDecision.NotApplicable
        // Use lastPathSegment so query strings and fragments don't break the .pdf check
        // (e.g. signed URLs like https://cdn.example.com/report.pdf?auth=token).
        val pathEndsInPdf = url.toUri().lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true
        if (mimeType != "application/pdf" && !pathEndsInPdf) return PdfRenderDecision.NotApplicable
        if (contentDisposition != null && contentDisposition.trim().startsWith("attachment", ignoreCase = true)) {
            return PdfRenderDecision.NotApplicable
        }
        if (Build.VERSION.SDK_INT < 31) return PdfRenderDecision.Fallback
        return PdfRenderDecision.Inline
    }

    private val cacheDir: File
        get() = File(context.cacheDir, PDF_CACHE_DIR).also { it.mkdirs() }

    override suspend fun downloadToCache(url: String, forceRefresh: Boolean): PdfDownloadResult = withContext(dispatcherProvider.io()) {
        val fileName = extractFileName(url)
        // Prefix the cache filename with the URL hash so two URLs sharing a last path segment
        // (e.g. report.pdf at site A and site B) don't collide and serve stale content.
        val targetFile = File(cacheDir, "${url.hashCode()}-$fileName")
        try {
            if (!forceRefresh && targetFile.exists() && hasPdfMagicBytes(targetFile)) {
                // Bump mtime so LRU eviction treats this file as recently *used*,
                // not just recently *written*.
                targetFile.setLastModified(System.currentTimeMillis())
                return@withContext PdfDownloadResult.Success(Uri.fromFile(targetFile))
            }

            val requestBuilder = Request.Builder().url(url)

            val cookie = cookieManagerProvider.get()?.getCookie(url)
            if (cookie != null) {
                requestBuilder.addHeader("Cookie", cookie)
            }

            executeRequestCancellably(okHttpClient.newCall(requestBuilder.build())).use { response ->
                if (!response.isSuccessful) {
                    logcat { "PDF download failed: HTTP ${response.code}" }
                    return@withContext PdfDownloadResult.Failure(PdfErrorType.UNKNOWN)
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
                    return@withContext PdfDownloadResult.Failure(PdfErrorType.UNKNOWN)
                }
            }

            coroutineContext.ensureActive()

            if (!hasPdfMagicBytes(targetFile)) {
                logcat { "PDF download failed: file does not start with %PDF magic bytes" }
                targetFile.delete()
                return@withContext PdfDownloadResult.Failure(PdfErrorType.UNKNOWN)
            }

            enforceCacheBudget(keepFile = targetFile, maxFiles = MAX_CACHED_FILES)

            PdfDownloadResult.Success(Uri.fromFile(targetFile))
        } catch (e: CancellationException) {
            logcat { "PDF download cancelled, cleaning up partial file" }
            targetFile.delete()
            throw e
        } catch (e: IOException) {
            logcat { "PDF download failed: ${e.message}" }
            targetFile.delete()
            PdfDownloadResult.Failure(PdfErrorType.IO_ERROR)
        } catch (e: SecurityException) {
            logcat { "PDF download denied: ${e.message}" }
            PdfDownloadResult.Failure(PdfErrorType.SECURITY_ERROR)
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

    /**
     * Cap the PDF cache at [maxFiles] entries using LRU eviction.
     *
     * Counts all files in the cache directory (including [keepFile]) and, if the
     * total exceeds [maxFiles], deletes the oldest non-keep files until exactly
     * [maxFiles] remain. [keepFile] is never evicted, so a freshly-written entry
     * always survives even when it's the one that pushed the cache over the cap.
     */
    @VisibleForTesting
    internal fun enforceCacheBudget(keepFile: File, maxFiles: Int) {
        val dir = cacheDir
        val keepName = keepFile.name
        val candidates = dir.listFiles()?.filter { it.isFile && it.name != keepName } ?: return
        val totalFiles = candidates.size + 1
        if (totalFiles <= maxFiles) return

        val toEvict = totalFiles - maxFiles
        candidates.sortedBy { it.lastModified() }.take(toEvict).forEach { it.delete() }
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
        private const val MAX_CACHED_FILES = 10
    }
}
