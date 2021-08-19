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

package com.duckduckgo.app.dev.settings.api

import com.duckduckgo.app.dev.settings.db.DevSettingsDataStore
import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppObjectGraph::class,
    boundType = ApiInterceptorPlugin::class
)
class ApiDevTdsInterceptor @Inject constructor(
    private val devSettingsDataStore: DevSettingsDataStore
) : ApiInterceptorPlugin, Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()

        val url = chain.request().url
        if (url.toString().contains("https://staticcdn.duckduckgo.com/trackerblocking/v2.1/")) {
            val tds = if (devSettingsDataStore.nextTdsEnabled) {
                "next-tds.json"
            } else {
                "tds.json"
            }
            request.url("https://staticcdn.duckduckgo.com/trackerblocking/v2.1/$tds")
        }

        return chain.proceed(request.build())
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}
