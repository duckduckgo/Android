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

package com.duckduckgo.networkprotection.internal.network

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.configuration.NETP_ENVIRONMENT_URL
import com.duckduckgo.networkprotection.internal.feature.NetPInternalFeatureToggles
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class NetPInternalEnvInterceptor @Inject constructor(
    private val vpnInternalFeatures: NetPInternalFeatureToggles,
    private val netPInternalEnvDataStore: NetPInternalEnvDataStore,
) : ApiInterceptorPlugin, Interceptor {
    override fun getInterceptor(): Interceptor = this
    override fun intercept(chain: Chain): Response {
        val encodedPath = chain.request().url.encodedPath

        if (vpnInternalFeatures.useVpnStagingEnvironment().isEnabled() && chain.request().url.toString().contains(NETP_ENVIRONMENT_URL)) {
            val newRequest = chain.request().newBuilder()

            val changedUrl = netPInternalEnvDataStore.getVpnStagingEndpoint() + encodedPath
            logcat { "NetP environment changed to $changedUrl" }
            newRequest.url(changedUrl)
            return chain.proceed(newRequest.build())
        }

        return chain.proceed(chain.request())
    }
}
