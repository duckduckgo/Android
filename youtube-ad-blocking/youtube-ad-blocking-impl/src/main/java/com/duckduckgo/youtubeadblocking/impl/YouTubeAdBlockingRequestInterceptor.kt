/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.youtubeadblocking.impl

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.inject.Inject

/**
 * Intercepts YouTube HTML document requests via shouldInterceptRequest and injects
 * a scriptlet bundle into the <head> of the response.
 *
 * This approach (Mechanism B) avoids addDocumentStartJavaScript entirely:
 * 1. Detects YouTube main-frame / iframe HTML document requests
 * 2. Fetches the response via OkHttp (forwarding original headers)
 * 3. Strips Content-Security-Policy headers (YouTube's CSP blocks inline scripts)
 * 4. Prepends <script>{scriptlet}</script> immediately after <head>
 * 5. Returns the modified WebResourceResponse to the WebView
 *
 * Because the HTML is modified before the parser sees it, the injected script
 * executes before any page JavaScript — equivalent to document_start timing.
 */
interface YouTubeAdBlockingRequestInterceptor {
    suspend fun intercept(
        request: WebResourceRequest,
        url: Uri,
    ): WebResourceResponse?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealYouTubeAdBlockingRequestInterceptor @Inject constructor(
    private val context: Context,
    private val youTubeAdBlockingFeature: YouTubeAdBlockingFeature,
    private val dispatcherProvider: DispatcherProvider,
) : YouTubeAdBlockingRequestInterceptor {

    /**
     * Plain OkHttpClient without API interceptors. The @Named("api") client adds a DDG API
     * User-Agent header that causes YouTube to reject the request ("device not supported").
     * We need to forward the WebView's original headers (including its browser User-Agent) faithfully.
     */
    private val okHttpClient: OkHttpClient by lazy { OkHttpClient() }

    private var cachedProbeScript: String? = null

    override suspend fun intercept(
        request: WebResourceRequest,
        url: Uri,
    ): WebResourceResponse? {
        if (!isYouTubeHtmlRequest(request, url)) return null

        return withContext(dispatcherProvider.io()) {
            try {
                fetchAndInject(request, url)
            } catch (e: IOException) {
                logcat(ERROR) { "YouTubeAdBlocking: Failed to fetch YouTube page: ${e.message}" }
                null
            } catch (e: Exception) {
                logcat(ERROR) { "YouTubeAdBlocking: Unexpected error during interception: ${e.message}" }
                null
            }
        }
    }

    private fun isYouTubeHtmlRequest(request: WebResourceRequest, url: Uri): Boolean {
        // Only intercept GET requests
        if (request.method != "GET") return false

        // Check host is YouTube
        val host = url.host?.removePrefix("www.") ?: return false
        if (host != YOUTUBE_HOST && host != YOUTUBE_MOBILE_HOST) return false

        // For main frame requests, always intercept (these are page navigations)
        if (request.isForMainFrame) return true

        // For sub-frame requests (iframes), check Accept header for text/html
        val acceptHeader = request.requestHeaders["Accept"] ?: return false
        return acceptHeader.contains("text/html")
    }

    private fun fetchAndInject(request: WebResourceRequest, url: Uri): WebResourceResponse? {
        val probeScript = getProbeScript() ?: return null

        // Build OkHttp request forwarding original headers
        val requestBuilder = Request.Builder().url(url.toString())
        request.requestHeaders.forEach { (key, value) ->
            // Skip headers that OkHttp sets automatically
            if (key.equals("Host", ignoreCase = true)) return@forEach
            requestBuilder.addHeader(key, value)
        }

        val okHttpRequest = requestBuilder.build()
        val response = okHttpClient.newCall(okHttpRequest).execute()

        if (!response.isSuccessful) {
            response.close()
            return null
        }

        val contentType = response.header("Content-Type") ?: "text/html"
        // Only inject into HTML responses
        if (!contentType.contains("text/html", ignoreCase = true)) {
            response.close()
            return null
        }

        val originalBody = response.body?.string() ?: run {
            response.close()
            return null
        }

        // Sync Set-Cookie headers into WebView's CookieManager so cookies are persisted.
        // WebView doesn't process Set-Cookie from synthetic WebResourceResponse objects.
        syncCookies(url.toString(), response.headers)

        // Inject script into <head>
        val injectedBody = injectScript(originalBody, probeScript)

        // Build response headers, stripping CSP
        val responseHeaders = buildResponseHeaders(response.headers)

        // Determine charset from content-type
        val charset = extractCharset(contentType) ?: "UTF-8"
        val mimeType = extractMimeType(contentType)

        val modifiedStream = ByteArrayInputStream(injectedBody.toByteArray(charset(charset)))

        logcat { "YouTubeAdBlocking: Injected probe script into ${url.host}${url.path}" }

        return WebResourceResponse(
            mimeType,
            charset,
            response.code,
            reasonPhraseFor(response.code),
            responseHeaders,
            modifiedStream,
        )
    }

    private fun injectScript(html: String, script: String): String {
        val scriptTag = "<script>$script</script>"

        // Try to inject right after <head> (or <head ...>)
        val headPattern = Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        val match = headPattern.find(html)
        if (match != null) {
            val insertPos = match.range.last + 1
            return html.substring(0, insertPos) + scriptTag + html.substring(insertPos)
        }

        // Fallback: prepend before <!DOCTYPE> or at the start
        return scriptTag + html
    }

    private fun syncCookies(url: String, headers: okhttp3.Headers) {
        try {
            val cookieManager = CookieManager.getInstance() ?: return
            headers.values("Set-Cookie").forEach { cookie ->
                cookieManager.setCookie(url, cookie)
            }
            cookieManager.flush()
        } catch (e: Exception) {
            logcat(ERROR) { "YouTubeAdBlocking: Failed to sync cookies: ${e.message}" }
        }
    }

    private fun buildResponseHeaders(originalHeaders: okhttp3.Headers): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        for (i in 0 until originalHeaders.size) {
            val name = originalHeaders.name(i)
            // Strip CSP headers — YouTube's CSP blocks inline scripts
            if (name.equals("Content-Security-Policy", ignoreCase = true)) continue
            if (name.equals("Content-Security-Policy-Report-Only", ignoreCase = true)) continue
            // Strip Content-Length since we modified the body
            if (name.equals("Content-Length", ignoreCase = true)) continue
            // Strip Content-Encoding since OkHttp already decoded it
            if (name.equals("Content-Encoding", ignoreCase = true)) continue
            headers[name] = originalHeaders.value(i)
        }
        return headers
    }

    private fun getProbeScript(): String? {
        cachedProbeScript?.let { return it }
        return try {
            context.resources.openRawResource(R.raw.youtube_ad_blocking_probe)
                .bufferedReader()
                .use { it.readText() }
                .also { cachedProbeScript = it }
        } catch (e: Exception) {
            logcat(ERROR) { "YouTubeAdBlocking: Failed to load probe script: ${e.message}" }
            null
        }
    }

    private fun extractCharset(contentType: String): String? {
        val charsetMatch = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(contentType)
        return charsetMatch?.groupValues?.get(1)
    }

    private fun extractMimeType(contentType: String): String {
        return contentType.substringBefore(";").trim()
    }

    private fun reasonPhraseFor(code: Int): String {
        return when (code) {
            200 -> "OK"
            301 -> "Moved Permanently"
            302 -> "Found"
            304 -> "Not Modified"
            else -> "OK"
        }
    }

    companion object {
        private const val YOUTUBE_HOST = "youtube.com"
        private const val YOUTUBE_MOBILE_HOST = "m.youtube.com"
    }
}
