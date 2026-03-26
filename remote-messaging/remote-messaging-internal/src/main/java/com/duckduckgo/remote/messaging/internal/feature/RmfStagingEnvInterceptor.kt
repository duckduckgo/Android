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

package com.duckduckgo.remote.messaging.internal.feature

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.internal.setting.RmfInternalSettings
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class RmfStagingEnvInterceptor @Inject constructor(
    private val rmfInternalSettings: RmfInternalSettings,
) : ApiInterceptorPlugin, Interceptor {
    override fun getInterceptor(): Interceptor = this
    override fun intercept(chain: Chain): Response {
        val lastSegment = chain.request().url.encodedPathSegments.last()

        if (rmfInternalSettings.useStatingEndpoint().isEnabled() && chain.request().url.isProductionEnvironment()) {
            val newRequest = chain.request().newBuilder()

            val changedUrl = RMF_STAGING_ENV + lastSegment
            logcat { "RMF environment changed to $changedUrl" }
            newRequest.url(changedUrl)
            return chain.proceed(newRequest.build())
        }

        return chain.proceed(chain.request())
    }

    private fun HttpUrl.isProductionEnvironment(): Boolean {
        return this.toString().let {
            it.contains(RMF_REQUEST) && !it.contains(RMF_STAGING_ENV)
        }
    }

    companion object {
        private const val RMF_REQUEST = "https://staticcdn.duckduckgo.com/remotemessaging/"
        private const val RMF_STAGING_ENV = "https://staticcdn.duckduckgo.com/remotemessaging/config/staging/"
    }
}
