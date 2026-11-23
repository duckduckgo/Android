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

package com.duckduckgo.adblocking.impl.pixels

import com.duckduckgo.adblocking.impl.segmentskip.SegmentSkipUserPreferences
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject

@ContributesIntoSet(scope = AppScope::class, binding = binding<PixelInterceptorPlugin>())
class SegmentSkipTelemetryPixelInterceptor @Inject constructor(
    private val preferences: SegmentSkipUserPreferences,
) : Interceptor, PixelInterceptorPlugin {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val name = request.url.pathSegments.last()
        if (!name.startsWith(TELEMETRY_PREFIX, ignoreCase = true)) return chain.proceed(request)

        val (code, message) = when (preferences.cachedConsent()) {
            true -> return chain.proceed(request)
            false -> 200 to "Dropped Segment Skip telemetry"
            null -> 503 to "Segment Skip consent not loaded"
        }
        return Response.Builder()
            .code(code)
            .protocol(Protocol.HTTP_2)
            .body(message.toResponseBody())
            .message(message)
            .request(request)
            .build()
    }

    override fun getInterceptor(): Interceptor = this

    private companion object {
        const val TELEMETRY_PREFIX = "webTelemetry_segmentSkip"
    }
}
