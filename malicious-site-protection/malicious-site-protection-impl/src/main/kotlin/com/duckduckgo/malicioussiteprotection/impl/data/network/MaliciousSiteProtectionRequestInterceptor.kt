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

package com.duckduckgo.malicioussiteprotection.impl.data.network

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.impl.BuildConfig
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import retrofit2.Invocation
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class MaliciousSiteProtectionRequestInterceptor @Inject constructor() : ApiInterceptorPlugin, Interceptor {
    override fun getInterceptor(): Interceptor = this

    override fun intercept(chain: Chain): Response {
        val request = chain.request()

        val authRequired = chain.request().tag(Invocation::class.java)
            ?.method()
            ?.isAnnotationPresent(AuthRequired::class.java) == true

        return if (authRequired) {
            val newRequest = chain.request().newBuilder()
            newRequest.addHeader(
                name = "X-Auth-Token",
                value = BuildConfig.MALICIOUS_SITE_PROTECTION_AUTH_TOKEN,
            )
            chain.proceed(
                newRequest.build().also { logcat { "headers: ${it.headers}" } },
            )
        } else {
            chain.proceed(request)
        }
    }
}
