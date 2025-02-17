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

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import com.squareup.moshi.Json
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming

@ContributesServiceApi(AppScope::class)
interface DbpService {
    @AuthRequired
    @GET("$BASE_URL/main_config.json")
    suspend fun getMainConfig(
        @Header("If-None-Match") etag: String?,
    ): Response<PirMainConfig>

    @AuthRequired
    @GET("$BASE_URL?name=all.zip&type=spec")
    @Streaming
    suspend fun getBrokerJsonFiles(): ResponseBody

    companion object {
        private const val BASE_URL = "https://dbp.duckduckgo.com/dbp/remote/v0"
    }

    data class PirMainConfig(
        @field:Json(name = "main_config_etag")
        val etag: String,
        @field:Json(name = "json_etags")
        val jsonEtags: PirBrokerEtags,
        @field:Json(name = "active_data_brokers")
        val activeBrokers: List<String>,
    )

    data class PirBrokerEtags(
        @field:Json(name = "current")
        val current: Map<String, String>,
    )

    data class PirJsonBroker(
        val name: String,
        val url: String,
        val version: String,
        val parent: String?,
        val addedDatetime: Long,
        val optOutUrl: String,
        @field:Json(name = "steps")
        val steps: List<String>,
        val schedulingConfig: PirJsonBrokerSchedulingConfig,
    )

    data class PirJsonBrokerSchedulingConfig(
        val retryError: Int,
        val confirmOptOutScan: Int,
        val maintenanceScan: Int,
        val maxAttempts: Int?,
    )
}
