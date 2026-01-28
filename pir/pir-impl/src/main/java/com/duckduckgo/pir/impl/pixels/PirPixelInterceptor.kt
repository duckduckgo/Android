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

package com.duckduckgo.pir.impl.pixels

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class PirPixelInterceptor @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : PixelInterceptorPlugin, Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()

        val url = if (ALLOWLIST.any { prefix -> pixel.startsWith(prefix) }) {
            chain.request().url.newBuilder()
                .addQueryParameter(
                    KEY_MANUFACTURER,
                    appBuildConfig.manufacturer,
                ).build()
        } else {
            chain.request().url
        }

        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor(): Interceptor = this

    companion object {
        private const val KEY_MANUFACTURER = "manufacturer"
        private val ALLOWLIST = listOf(
            "m_dbp_foreground-run_started",
            "m_dbp_foreground-run_completed",
            "m_dbp_foreground-run_start-failed",
            "m_dbp_foreground-run_low-memory",
            "m_dbp_scheduled-run_started",
            "m_dbp_scheduled-run_completed",
            "m_dbp_email-confirmation_started",
            "m_dbp_email-confirmation_completed",
        )
    }
}
