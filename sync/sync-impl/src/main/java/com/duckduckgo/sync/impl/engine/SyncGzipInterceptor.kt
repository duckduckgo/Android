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

import android.util.Base64
import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.SyncService
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import java.io.IOException
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class SyncGzipInterceptor @Inject constructor(
    private val syncFeature: SyncFeature,
    private val pixel: Pixel,
) : ApiInterceptorPlugin, Interceptor {

    override fun intercept(chain: Chain): Response {
        val isSyncEndpoint = chain.request().url.toString().contains(SyncService.SYNC_PROD_ENVIRONMENT_URL) ||
            chain.request().url.toString().contains(SyncService.SYNC_DEV_ENVIRONMENT_URL)

        if (!isSyncEndpoint) {
            return chain.proceed(chain.request())
        }

        // check if it's http operation is PATCH
        if (chain.request().method == "PATCH" && syncFeature.gzipPatchRequests().isEnabled()) {
            kotlin.runCatching {
                val originalRequest = chain.request()
                val body = originalRequest.body ?: return chain.proceed(originalRequest)
                val compressedRequest = originalRequest.newBuilder()
                    .header("Content-Encoding", "gzip")
                    .method(originalRequest.method, forceContentLength(body.gzip()))
                    .build()
                return chain.proceed(compressedRequest)
            }.onFailure {
                val params = mapOf(
                    SyncPixelParameters.ERROR to Base64.encodeToString(
                        it.stackTraceToString().toByteArray(),
                        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
                    ),
                )
                pixel.fire(SyncPixelName.SYNC_PATCH_COMPRESS_FAILED, params)
                // if there is an exception, proceed with the original request
                return chain.proceed(chain.request())
            }
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
