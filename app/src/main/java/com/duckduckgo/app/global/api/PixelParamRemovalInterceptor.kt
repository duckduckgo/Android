/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.api

import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter.APP_VERSION
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter.ATB
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.voice.impl.VoiceSearchPixelNames
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class PixelParamRemovalInterceptor @Inject constructor(
    private val pixelsPlugin: PluginPoint<PixelParamRemovalPlugin>,
) : Interceptor, PixelInterceptorPlugin {

    val pixels by lazy {
        pixelsPlugin.getPlugins().flatMap { it.names() }.toSet()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()
        val url = chain.request().url.newBuilder().apply {
            val atbs = pixels.filter { it.second.contains(ATB) }.map { it.first }
            val versions = pixels.filter { it.second.contains(APP_VERSION) }.map { it.first }
            if (atbs.any { pixel.startsWith(it) }) {
                removeAllQueryParameters(AppUrl.ParamKey.ATB)
            }
            if (versions.any { pixel.startsWith(it) }) {
                removeAllQueryParameters(Pixel.PixelParameter.APP_VERSION)
            }
        }.build()

        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelParamRemovalPlugin::class,
)
object PixelInterceptorPixelsRequiringDataCleaning : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            AppPixelName.EMAIL_COPIED_TO_CLIPBOARD.pixelName to PixelParameter.removeAll(),
            StatisticsPixelName.BROWSER_DAILY_ACTIVE_FEATURE_STATE.pixelName to PixelParameter.removeAll(),
            VoiceSearchPixelNames.VOICE_SEARCH_ERROR.pixelName to PixelParameter.removeAll(),
        )
    }
}
