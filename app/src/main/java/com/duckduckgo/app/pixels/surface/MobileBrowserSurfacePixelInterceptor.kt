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

package com.duckduckgo.app.pixels.surface

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class MobileBrowserSurfacePixelInterceptor @Inject constructor(
    private val pixelsPlugin: PluginPoint<SurfacePixelPlugin>,
    private val productSurfaceTelemetryFeature: ProductSurfaceTelemetryFeature,
) : Interceptor, PixelInterceptorPlugin {

    override fun intercept(chain: Chain): Response {
        val pixelName = chain.request().url.pathSegments.last()

        val shouldCheckFeatureFlag = pixelsPlugin.getPlugins().any { plugin ->
            plugin.names().any { pixelName.startsWith(it) }
        }

        if (shouldCheckFeatureFlag) {
            if (!productSurfaceTelemetryFeature.self().isEnabled()) {
                logcat { "Mobile surfaces pixel dropped: $pixelName (feature disabled)" }
                return dummyResponse(chain)
            } else {
                logcat { "Mobile surfaces pixel sending: $pixelName" }
            }
        }

        return chain.proceed(chain.request())
    }

    private fun dummyResponse(chain: Chain): Response {
        return Response.Builder()
            .code(500)
            .protocol(Protocol.HTTP_2)
            .body("Mobile surfaces pixel dropped".toResponseBody())
            .message("Dropped mobile surfaces pixel")
            .request(chain.request())
            .build()
    }

    override fun getInterceptor(): Interceptor = this
}
