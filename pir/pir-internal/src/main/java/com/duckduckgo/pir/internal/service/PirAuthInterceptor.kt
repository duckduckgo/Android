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

package com.duckduckgo.pir.internal.service

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesMultibinding
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import retrofit2.Invocation

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class PirAuthInterceptor @Inject constructor(
    private val subscriptions: Subscriptions,
) : ApiInterceptorPlugin, Interceptor {
    override fun getInterceptor(): Interceptor = this

    override fun intercept(chain: Chain): Response {
        val request = chain.request()

        val authRequired = request.tag(Invocation::class.java)
            ?.method()
            ?.isAnnotationPresent(PirAuthRequired::class.java) == true

        return if (authRequired) {
            val accessToken = runBlocking { subscriptions.getAccessToken() }
                ?: throw IOException("Can't obtain access token for request")

            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "bearer $accessToken")
                .build()

            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(request)
        }
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PirAuthRequired
