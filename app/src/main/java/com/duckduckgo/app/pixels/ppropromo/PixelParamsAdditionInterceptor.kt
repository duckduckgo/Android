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

package com.duckduckgo.app.pixels.ppropromo

import com.duckduckgo.app.pixels.ppropromo.params.AdditionalPixelParamsGenerator
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class PixelParamsAdditionInterceptor @Inject constructor(
    private val pixelsPlugin: PluginPoint<PixelParamsAdditionPlugin>,
    private val additionalPixelParamsGenerator: AdditionalPixelParamsGenerator,
    private val additionalPproPixelParamsFeature: AdditionalPproPixelParamsFeature,
) : Interceptor, PixelInterceptorPlugin {
    override fun intercept(chain: Chain): Response {
        val url = chain.request().url.newBuilder()
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()
        val queryParamsString = chain.request().url.query

        if (additionalPproPixelParamsFeature.self().isEnabled() && queryParamsString != null) {
            pixelsPlugin.getPlugins().forEach { plugin ->
                if (plugin.names().any { pixel.startsWith(it) }) {
                    val queryParams = queryParamsString.toParamsMap()
                    if (plugin.isEligible(queryParams)) {
                        runBlocking {
                            additionalPixelParamsGenerator.generateAdditionalParams().forEach { (key, value) ->
                                url.addQueryParameter(key, value)
                            }
                        }
                    }
                }
            }
        }

        return chain.proceed(request.url(url.build()).build())
    }

    private fun String.toParamsMap(): Map<String, String> {
        return split("&").associate {
            val param = it.split("=")
            param[0] to param[1]
        }
    }

    override fun getInterceptor(): Interceptor = this
}
