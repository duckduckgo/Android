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

import com.duckduckgo.app.global.device.DeviceInfo
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * This interceptor fixes the re-query pixels without being invasive to the Pixel APIs. When the
 * search box was removed, the rq pixels were moved into the mobile app, however ALL pixels sent
 * from the mobile app have _android_{formFactor} appended to the pixel name, and this broke all
 * existing monitoring and dashboards.
 *
 * This interceptor removes the _android_{formFactor} appended suffix
 */
class PixelReQueryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var url = chain.request().url
        val request = chain.request().newBuilder()

        url =
            url.toUrl()
                .toString()
                .replace("rq_0_android_${DeviceInfo.FormFactor.PHONE.description}", "rq_0")
                .toHttpUrl()
        url =
            url.toUrl()
                .toString()
                .replace("rq_0_android_${DeviceInfo.FormFactor.TABLET.description}", "rq_0")
                .toHttpUrl()
        url =
            url.toUrl()
                .toString()
                .replace("rq_1_android_${DeviceInfo.FormFactor.PHONE.description}", "rq_1")
                .toHttpUrl()
        url =
            url.toUrl()
                .toString()
                .replace("rq_1_android_${DeviceInfo.FormFactor.TABLET.description}", "rq_1")
                .toHttpUrl()

        Timber.d("Pixel interceptor: $url")

        return chain.proceed(request.url(url).build())
    }
}
