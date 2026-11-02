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

package com.duckduckgo.app.mockenvironment

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.app.trackerdetection.api.TDS_BASE_URL
import com.duckduckgo.app.trackerdetection.api.TdsRequired
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Invocation
import javax.inject.Inject

/**
 * In the mocked test environment, answers the TDS and surrogates requests from the pinned [MockEnvironmentSnapshot]; everything else passes through.
 *
 * Plugin ordering is undefined, so matching works whether or not `BlockListInterceptorApiPlugin` already rewrote the TDS URL:
 * the `@TdsRequired` tag survives `newBuilder()` and the rewritten URL still ends in `android-tds.json`.
 */
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<ApiInterceptorPlugin>(),
)
class MockEnvironmentApiInterceptor @Inject constructor(
    private val store: MockEnvironmentStore,
    private val snapshot: MockEnvironmentSnapshot,
) : ApiInterceptorPlugin, Interceptor {

    override fun getInterceptor(): Interceptor = this

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!store.isEnabled) return chain.proceed(request)

        return when {
            request.isTds() -> pinned(request, snapshot.tds, "application/json", snapshot.tdsEtag)
            request.url.isSurrogates() -> pinned(request, snapshot.surrogates, "text/plain", etag = null)
            else -> chain.proceed(request)
        }
    }

    private fun pinned(
        request: Request,
        body: ByteArray,
        contentType: String,
        etag: String?,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .apply { etag?.let { header("ETag", "\"$it\"") } }
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()
    }

    private fun HttpUrl.isSurrogates(): Boolean = host == SURROGATES_URL.host && encodedPath == SURROGATES_URL.encodedPath

    private fun Request.isTds(): Boolean {
        val tagged = tag(Invocation::class.java)?.method()?.isAnnotationPresent(TdsRequired::class.java) == true
        val path = url.encodedPath
        val tdsPath = url.host == TDS_HOST && path.startsWith(TDS_PATH_PREFIX) && !path.contains("/appTP/") && path.endsWith(TDS_FILE_NAME)
        return tagged || tdsPath
    }

    private companion object {
        val SURROGATES_URL = "https://staticcdn.duckduckgo.com/surrogates.txt".toHttpUrl()
        val TDS_HOST = TDS_BASE_URL.toHttpUrl().host
        val TDS_PATH_PREFIX = TDS_BASE_URL.toHttpUrl().encodedPath
        const val TDS_FILE_NAME = "android-tds.json"
    }
}
