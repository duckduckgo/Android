/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.sync.impl.engine

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.SyncService
import com.duckduckgo.sync.impl.internal.SyncInternalEnvDataStore
import com.squareup.anvil.annotations.ContributesMultibinding
import java.io.IOException
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.buffer

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class SyngGzipInterceptor @Inject constructor(
    private val envDataStore: SyncInternalEnvDataStore,
    private val syncFeature: SyncFeature,
) : ApiInterceptorPlugin, Interceptor {

    override fun intercept(chain: Chain): Response {
        val isSyncEndpoint = chain.request().url.toString().contains(SyncService.SYNC_PROD_ENVIRONMENT_URL) ||
            chain.request().url.toString().contains(SyncService.SYNC_DEV_ENVIRONMENT_URL)

        if (!isSyncEndpoint) {
            return chain.proceed(chain.request())
        }

        // check if it's http operation is PATCH
        if (chain.request().method == "PATCH" && syncFeature.gzipPatchRequests().isEnabled()) {
            val originalRequest = chain.request()
            val body = originalRequest.body ?: return chain.proceed(originalRequest)
            val compressedRequest = originalRequest.newBuilder()
                .header("Content-Encoding", "gzip")
                .method(originalRequest.method, forceContentLength(body.gzip()))
                .build()
            return chain.proceed(compressedRequest)
        }

        return chain.proceed(chain.request())
    }

    /** https://github.com/square/okhttp/issues/350 */
    @Throws(IOException::class)
    private fun forceContentLength(requestBody: RequestBody): RequestBody? {
        val buffer = Buffer()
        requestBody.writeTo(buffer)
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return requestBody.contentType()
            }

            override fun contentLength(): Long {
                return buffer.size
            }

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                sink.write(buffer.snapshot())
            }
        }
    }

    fun RequestBody.gzip(): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return this@gzip.contentType()
            }

            override fun contentLength(): Long {
                return -1 // We don't know the compressed length in advance!
            }

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                val gzipSink = GzipSink(sink).buffer()
                this@gzip.writeTo(gzipSink)
                gzipSink.close()
            }

            override fun isOneShot(): Boolean {
                return this@gzip.isOneShot()
            }
        }
    }

    override fun getInterceptor() = this
}
