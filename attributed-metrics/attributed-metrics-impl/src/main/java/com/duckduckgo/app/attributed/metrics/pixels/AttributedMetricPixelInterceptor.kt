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

package com.duckduckgo.app.attributed.metrics.pixels

import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class AttributedMetricPixelInterceptor @Inject constructor() : Interceptor, PixelInterceptorPlugin {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        var url = chain.request().url
        val pixel = chain.request().url.pathSegments.last()
        if (pixel.startsWith(ATTRIBUTED_METRICS_PIXEL_PREFIX)) {
            url = url.toUrl().toString().replace("android_${DeviceInfo.FormFactor.PHONE.description}", "android").toHttpUrl()
            logcat(tag = "AttributedMetrics") {
                "Pixel renamed to: $url"
            }
        }
        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor() = this

    companion object {
        const val ATTRIBUTED_METRICS_PIXEL_PREFIX = "attributed_metric"
    }
}
