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

package com.duckduckgo.app.global.api

import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject

/**
 * Removes app version information from select pixels
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class
)
class AppVersionPixelRemovalInterceptor @Inject constructor() : Interceptor, PixelInterceptorPlugin {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()
        val url = if (pixel_prefixes.firstOrNull { pixel.startsWith(it) } != null) {
            chain.request().url.newBuilder()
                .removeAllQueryParameters(Pixel.PixelParameter.APP_VERSION)
                .build()
        } else {
            chain.request().url.newBuilder().build()
        }

        Timber.d("Pixel interceptor: $url")

        return chain.proceed(request.url(url).build())
    }

    companion object {
        // list of pixels for which we'll remove App version information
        @VisibleForTesting
        val pixel_prefixes = listOf(
            "m_atp_ev_cpu_usage_"
        )
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}
