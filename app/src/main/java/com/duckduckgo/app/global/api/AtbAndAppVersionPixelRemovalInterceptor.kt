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

import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.app.global.plugins.pixel.PixelRequiringDataCleaningPlugin
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class AtbAndAppVersionPixelRemovalInterceptor @Inject constructor(
    private val pixelsPlugin: PluginPoint<PixelRequiringDataCleaningPlugin>,
) : Interceptor, PixelInterceptorPlugin {

    val pixels: Set<String> by lazy {
        pixelsPlugin.getPlugins().flatMap { it.names() }.toSet()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()
        val url = if (isInPixelsList(pixel)) {
            chain.request().url.newBuilder()
                .removeAllQueryParameters(AppUrl.ParamKey.ATB)
                .removeAllQueryParameters(Pixel.PixelParameter.APP_VERSION)
                .build()
        } else {
            chain.request().url.newBuilder().build()
        }

        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor(): Interceptor {
        return this
    }

    private fun isInPixelsList(pixel: String): Boolean {
        return pixels.firstOrNull { pixel.startsWith(it) } != null
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelRequiringDataCleaningPlugin::class,
)
object PixelInterceptorPixelsRequiringDataCleaning : PixelRequiringDataCleaningPlugin {
    override fun names(): List<String> {
        return listOf(
            AppPixelName.EMAIL_COPIED_TO_CLIPBOARD.pixelName,
            StatisticsPixelName.BROWSER_DAILY_ACTIVE_FEATURE_STATE.pixelName,
            "m_atp_unprotected_apps_bucket_",
        )
    }
}
