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
        val name = chain.request().url.pathSegments.last()
        if (name in gatedNames && !preferences.hasConsent()) {
            return Response.Builder()
                .code(200)
                .protocol(Protocol.HTTP_2)
                .body("Segment Skip telemetry dropped".toResponseBody())
                .message("Dropped Segment Skip telemetry")
                .request(chain.request())
                .build()
        }
        return chain.proceed(chain.request())
    }

    override fun getInterceptor(): Interceptor = this

    private companion object {
        val gatedNames = setOf("segmentSkip_errors_hour", "segmentSkip_errors_day")
            .flatMap { name -> listOf("${name}_android_phone", "${name}_android_tablet") }.toSet()
    }
}
