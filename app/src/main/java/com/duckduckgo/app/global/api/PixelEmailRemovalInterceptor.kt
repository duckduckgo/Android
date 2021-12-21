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

import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import okhttp3.Interceptor
import okhttp3.Response

class PixelEmailRemovalInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()
        val url =
            if (pixels.contains(pixel.substringBefore("_android_"))) {
                chain
                    .request()
                    .url
                    .newBuilder()
                    .removeAllQueryParameters(AppUrl.ParamKey.ATB)
                    .removeAllQueryParameters(Pixel.PixelParameter.APP_VERSION)
                    .build()
            } else {
                chain.request().url.newBuilder().build()
            }

        return chain.proceed(request.url(url).build())
    }

    companion object {
        // list of pixels for which we'll remove the ATB and App version information
        @VisibleForTesting
        val pixels =
            listOf(
                AppPixelName.EMAIL_TOOLTIP_DISMISSED.pixelName,
                AppPixelName.EMAIL_USE_ALIAS.pixelName,
                AppPixelName.EMAIL_USE_ADDRESS.pixelName,
                AppPixelName.EMAIL_COPIED_TO_CLIPBOARD.pixelName)
    }
}
