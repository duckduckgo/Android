/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.blocklist

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.app.trackerdetection.api.TDS_BASE_URL
import com.duckduckgo.app.trackerdetection.blocklist.BlockList.Cohorts.CONTROL
import com.duckduckgo.app.trackerdetection.blocklist.BlockList.Cohorts.TREATMENT
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

class BlockListInterceptorApiPlugin constructor(
    private val inventory: FeatureTogglesInventory,
    private val moshi: Moshi,
) : Interceptor, ApiInterceptorPlugin {

    private val jsonAdapter: JsonAdapter<Map<String, String>> by lazy {
        moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))
    }
    override fun intercept(chain: Chain): Response {
        val request = chain.request().newBuilder()
        val url = chain.request().url
        if (!url.toString().contains(TDS_BASE_URL)) {
            return chain.proceed(request.build())
        }
        val activeExperiment = runBlocking {
            inventory.activeTdsFlag()
        }

        return activeExperiment?.let {
            val config = activeExperiment.getSettings()?.let {
                runCatching {
                    jsonAdapter.fromJson(it)
                }.getOrDefault(emptyMap())
            } ?: emptyMap()
            val path = when {
                activeExperiment.isEnabled(TREATMENT) -> config["treatmentUrl"]
                activeExperiment.isEnabled(CONTROL) -> config["controlUrl"]
                else -> config["nextUrl"]
            } ?: return chain.proceed(request.build())
            chain.proceed(request.url("$TDS_BASE_URL$path").build())
        } ?: chain.proceed(request.build())
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}
