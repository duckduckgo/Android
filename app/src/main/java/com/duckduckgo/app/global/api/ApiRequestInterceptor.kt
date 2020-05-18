/*
 * Copyright (c) 2018 DuckDuckGo
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

import android.content.Context
import com.duckduckgo.app.browser.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class ApiRequestInterceptor(context: Context) : Interceptor {

    private val userAgent: String by lazy {
        "ddg_android/${BuildConfig.VERSION_NAME} (${context.applicationInfo.packageName}; Android API ${android.os.Build.VERSION.SDK_INT})"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
            .newBuilder()
            .addHeader(Header.USER_AGENT, userAgent)
            .build()

        return chain.proceed(request)
    }
}
