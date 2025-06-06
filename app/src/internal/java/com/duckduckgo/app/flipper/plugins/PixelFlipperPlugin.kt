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

package com.duckduckgo.app.flipper.plugins

import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.facebook.flipper.core.FlipperConnection
import com.facebook.flipper.core.FlipperObject
import com.facebook.flipper.core.FlipperPlugin
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import java.time.LocalTime
import javax.inject.Inject
import logcat.LogPriority.VERBOSE
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Response

@SingleInstanceIn(AppScope::class)
class PixelFlipperPlugin @Inject constructor() :
    FlipperPlugin,
    Interceptor,
    PixelInterceptorPlugin {

    private var connection: FlipperConnection? = null

    override fun getId(): String {
        return "ddg-pixels"
    }

    override fun onConnect(connection: FlipperConnection?) {
        logcat(VERBOSE) { "$id: connected" }
        this.connection = connection
    }

    override fun onDisconnect() {
        logcat(VERBOSE) { "$id: disconnected" }
        connection = null
    }

    override fun runInBackground(): Boolean {
        logcat(VERBOSE) { "$id: running" }
        return false
    }

    private fun newRow(row: FlipperObject) {
        connection?.send("newData", row)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (connection == null) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()

        val pixelPayload = mutableMapOf<String, List<String?>>()
        request.url.queryParameterNames.forEach { param ->
            pixelPayload[param] = request.url.queryParameterValues(param)
        }
        FlipperObject.Builder()
            .put("time", LocalTime.now())
            .put("pixel", request.url.pathSegments.last())
            .put("payload", pixelPayload)
            .build()
            .also { newRow(it) }

        return chain.proceed(request)
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}

@Module
@ContributesTo(AppScope::class)
abstract class PixelFlipperPluginModule {
    @Binds
    @IntoSet
    abstract fun bindPixelFlipperPlugin(pixelFlipperPlugin: PixelFlipperPlugin): FlipperPlugin

    @Binds
    @IntoSet
    abstract fun bindPixelFlipperPluginInterceptor(pixelFlipperPlugin: PixelFlipperPlugin): PixelInterceptorPlugin
}
