/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.configuration

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.BuildConfig
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Response

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class WgVpnControllerRequestInterceptor @Inject constructor(
    private val netpWaitlistRepository: NetPWaitlistRepository,
    private val appBuildConfig: AppBuildConfig,
) : Interceptor, ApiInterceptorPlugin {
    override fun getInterceptor(): Interceptor = this

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val newRequest = chain.request().newBuilder()
        if (ENDPOINTS_PATTERN_MATCHER.any { url.toString().endsWith(it) }) {
            logcat { "Adding Authorization Bearer token to request $url" }
            newRequest.addHeader(
                name = "Authorization",
                // this runBlocking is fine as we're already in a background thread
                value = "bearer ${runBlocking { netpWaitlistRepository.getAuthenticationToken() }}",
            )

            if (appBuildConfig.isInternalBuild()) {
                newRequest.addHeader(
                    name = "NetP-Debug-Code",
                    value = BuildConfig.NETP_DEBUG_SERVER_TOKEN,
                )
            }
        }

        return chain.proceed(
            newRequest.build().also { logcat { "headers: ${it.headers}" } },
        )
    }

    companion object {
        // The NetP environments are for now https://<something>.netp.duckduckgo.com/<endpoint>
        private val ENDPOINTS_PATTERN_MATCHER = listOf(
            "netp.duckduckgo.com/servers",
            "netp.duckduckgo.com/register",
            "netp.duckduckgo.com/locations",
        )
    }
}
