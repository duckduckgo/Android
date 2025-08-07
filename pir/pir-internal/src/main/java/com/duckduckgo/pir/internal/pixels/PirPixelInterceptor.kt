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

package com.duckduckgo.pir.internal.pixels

import android.content.Context
import android.os.PowerManager
import android.util.Base64
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class PirPixelInterceptor @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
) : PixelInterceptorPlugin, Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()

        val url = if (pixel.startsWith(PIXEL_PREFIX) && !EXCEPTIONS.any { exception -> pixel.startsWith(exception) }) {
            chain.request().url.newBuilder()
                .addQueryParameter(
                    KEY_METADATA,
                    JSONObject()
                        .put("os", appBuildConfig.sdkInt)
                        .put("batteryOptimizations", (!isIgnoringBatteryOptimizations()).toString())
                        .put("man", appBuildConfig.manufacturer)
                        .toString().toByteArray().run {
                            Base64.encodeToString(this, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
                        },
                )
                .build()
        } else {
            chain.request().url
        }

        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor(): Interceptor = this

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return runCatching {
            context.packageName?.let {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } ?: false
        }.getOrDefault(false)
    }

    companion object {
        private const val KEY_METADATA = "metadata"
        private const val PIXEL_PREFIX = "pir_internal"
        private val EXCEPTIONS = emptyList<String>()
    }
}
