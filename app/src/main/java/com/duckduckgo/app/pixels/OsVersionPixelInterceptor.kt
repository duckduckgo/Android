/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.pixels

import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.OS_VERSION
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class
)
class OsVersionPixelInterceptor @Inject constructor(
    private val appBuildConfig: AppBuildConfig
) : PixelInterceptorPlugin, Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()

        val url = if (OS_VERSION_PIXELS.any { pixel.startsWith(it.pixelName) }) {
            chain.request().url.newBuilder().addQueryParameter(OS_VERSION, appBuildConfig.sdkInt.toString()).build()
        } else {
            chain.request().url
        }

        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor(): Interceptor {
        return this
    }

    companion object {
        private val OS_VERSION_PIXELS = listOf<Pixel.PixelName>(
            AppPixelName.DOWNLOAD_REQUEST_STARTED,
            AppPixelName.DOWNLOAD_REQUEST_FAILED,
            AppPixelName.DOWNLOAD_REQUEST_SUCCEEDED,
            StatisticsPixelName.APPLICATION_CRASH,
            StatisticsPixelName.APPLICATION_CRASH_GLOBAL
        )
    }
}
