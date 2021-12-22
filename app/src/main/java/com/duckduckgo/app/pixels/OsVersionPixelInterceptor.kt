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

package com.duckduckgo.app.pixels

import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.impl.pixels.DownloadsPixelName
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class
)
class OsVersionPixelInterceptor @Inject constructor(private val appBuildConfig: AppBuildConfig) : PixelInterceptorPlugin, Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()

        val url = if (OS_VERSION_PIXELS.any { pixelName -> pixel.startsWith(pixelName + PIXEL_OS_SUFFIX) }) {
            chain.request().url.newBuilder().addQueryParameter(PixelParameter.OS_VERSION, appBuildConfig.sdkInt.toString()).build()
        } else {
            chain.request().url
        }

        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor(): Interceptor {
        return this
    }

    companion object {
        private const val PIXEL_OS_SUFFIX = "_android"
        private val OS_VERSION_PIXELS = listOf(
            DownloadsPixelName.DOWNLOAD_REQUEST_SUCCEEDED.pixelName,
            DownloadsPixelName.DOWNLOAD_REQUEST_FAILED.pixelName,
            DownloadsPixelName.DOWNLOAD_REQUEST_STARTED.pixelName,
            StatisticsPixelName.APPLICATION_CRASH.pixelName,
            StatisticsPixelName.APPLICATION_CRASH_GLOBAL.pixelName
        )
    }
}
