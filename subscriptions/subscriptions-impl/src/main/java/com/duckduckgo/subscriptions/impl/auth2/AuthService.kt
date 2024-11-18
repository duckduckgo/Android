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

package com.duckduckgo.subscriptions.impl.auth2

import com.squareup.moshi.Json
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthService {
    @GET("https://quack.duckduckgo.com/api/auth/v2/authorize")
    suspend fun authorize(
        @Query("response_type") responseType: String,
        @Query("code_challenge") codeChallenge: String,
        @Query("code_challenge_method") codeChallengeMethod: String,
        @Query("client_id") clientId: String,
        @Query("redirect_uri") redirectUri: String,
        @Query("scope") scope: String,
    ): Response<Unit>

    @POST("https://quack.duckduckgo.com/api/auth/v2/account/create")
    suspend fun createAccount(
        @Header("Cookie") cookie: String,
    ): Response<Unit>

    @GET("https://quack.duckduckgo.com/api/auth/v2/token")
    suspend fun token(
        @Query("grant_type") grantType: String,
        @Query("client_id") clientId: String,
        @Query("code_verifier") codeVerifier: String?,
        @Query("code") code: String?,
        @Query("redirect_uri") redirectUri: String?,
        @Query("refresh_token") refreshToken: String?,
    ): TokensResponse

    @GET("https://quack.duckduckgo.com/api/auth/v2/.well-known/jwks.json")
    suspend fun jwks(): ResponseBody

    @POST("https://quack.duckduckgo.com/api/auth/v2/login")
    suspend fun login(
        @Header("Cookie") cookie: String,
        @Body body: StoreLoginBody,
    ): Response<Unit>

    @POST("https://quack.duckduckgo.com/api/auth/v2/exchange")
    suspend fun exchange(
        @Header("Authorization") authorization: String,
        @Header("Cookie") cookie: String,
    ): Response<Unit>
}

data class TokensResponse(
    @field:Json(name = "access_token") val accessToken: String,
    @field:Json(name = "refresh_token") val refreshToken: String,
)

data class StoreLoginBody(
    @field:Json(name = "method") val method: String,
    @field:Json(name = "signature") val signature: String,
    @field:Json(name = "source") val source: String,
    @field:Json(name = "google_signed_data") val googleSignedData: String,
    @field:Json(name = "google_package_name") val googlePackageName: String,
)
