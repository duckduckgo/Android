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

package com.duckduckgo.pir.impl.service

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import com.squareup.moshi.Json
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

@ContributesServiceApi(AppScope::class)
interface DbpService {
    @PirAuthRequired
    @GET("$BASE_URL/remote/v0/main_config.json")
    suspend fun getMainConfig(
        @Header("If-None-Match") etag: String?,
    ): Response<PirMainConfig>

    @PirAuthRequired
    @GET("$BASE_URL/remote/v0?name=all.zip&type=spec")
    @Streaming
    suspend fun getBrokerJsonFiles(): ResponseBody

    @PirAuthRequired
    @GET("$BASE_URL/em/v0/generate")
    suspend fun getEmail(
        @Query("dataBroker") dataBrokerUrl: String,
        @Query("attemptId") attemptId: String? = null,
    ): PirGetEmailResponse

    @PirAuthRequired
    @GET("$BASE_URL/em/v0/links")
    suspend fun getEmailStatus(
        @Query("e") emailAddress: String,
        @Query("attemptId") attemptId: String? = null,
    ): PirGetEmailStatusResponse

    @PirAuthRequired
    @POST("$BASE_URL/captcha/v0/submit")
    suspend fun submitCaptchaInformation(
        @Body body: PirStartCaptchaSolutionBody,
        @Query("attemptId") attemptId: String? = null,
    ): PirStartCaptchaSolutionResponse

    @PirAuthRequired
    @GET("$BASE_URL/captcha/v0/result")
    suspend fun getCaptchaSolution(
        @Query("transactionId") transactionId: String,
        @Query("attemptId") attemptId: String? = null,
    ): PirGetCaptchaSolutionResponse

    companion object {
        private const val BASE_URL = "https://dbp.duckduckgo.com/dbp"
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

    data class PirGetEmailResponse(
        val emailAddress: String,
        val pattern: String,
    )

    data class PirGetEmailStatusResponse(
        val link: String?,
        val status: String,
    )

    data class PirStartCaptchaSolutionBody(
        val siteKey: String,
        val url: String,
        val type: String,
        val backend: String? = null,
    )

    data class PirStartCaptchaSolutionResponse(
        val message: String,
        val transactionId: String,
    )

    data class PirGetCaptchaSolutionResponse(
        val message: String,
        val data: String,
        val meta: CaptchaSolutionMeta,
    )

    data class CaptchaSolutionMeta(
        val backends: Map<String, CaptchaSolutionBackend>,
        val type: String,
        val lastUpdated: Float,
        val lastBackend: String,
        val timeToSolution: Float,
    )

    data class CaptchaSolutionBackend(
        val solveAttempts: Int,
        val pollAttempts: Int,
        val error: Int,
    )
}
