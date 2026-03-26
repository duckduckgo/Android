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

package com.duckduckgo.app.global.image

import android.content.Context
import android.os.Build
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.duckduckgo.app.browser.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.InputStream

@GlideModule
class GlobalGlideModule : AppGlideModule() {
    override fun registerComponents(
        context: Context,
        glide: Glide,
        registry: Registry,
    ) {
        val okHttpClientBuilder = OkHttpClient.Builder()
            .addInterceptor { chain: Interceptor.Chain ->
                val request = chain.request()

                return@addInterceptor request.newBuilder()
                    .header("User-Agent", getGlideUserAgent(context))
                    .build().run {
                        chain.proceed(this)
                    }
            }

        // use our custom okHttp instead of default HTTPUrlConnection
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(okHttpClientBuilder.build()),
        )
    }

    private fun getGlideUserAgent(context: Context): String {
        return "DuckDuckGo/${BuildConfig.VERSION_NAME.split(".").first()} " +
            "(${context.applicationInfo.packageName}; Android API ${Build.VERSION.SDK_INT})"
    }
}
