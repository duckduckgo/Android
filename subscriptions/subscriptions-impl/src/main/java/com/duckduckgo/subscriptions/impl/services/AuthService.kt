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

package com.duckduckgo.subscriptions.impl.services

import com.duckduckgo.anvil.annotations.ContributesNonCachingServiceApi
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.repository.Entitlement
import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

@ContributesNonCachingServiceApi(AppScope::class)
interface AuthService {
    @POST("https://quack.duckduckgo.com/api/auth/account/create")
    suspend fun createAccount(@Header("Authorization") authorization: String?): CreateAccountResponse

    @POST("https://quack.duckduckgo.com/api/auth/store-login")
    suspend fun storeLogin(@Body storeLoginBody: StoreLoginBody): StoreLoginResponse

    /**
     * Validate token takes either an access token or an auth token
     */
    @GET("https://quack.duckduckgo.com/api/auth/validate-token")
    suspend fun validateToken(@Header("Authorization") authorization: String): ValidateTokenResponse

    /**
     * Exchanges an auth token for an access token
     */
    @GET("https://quack.duckduckgo.com/api/auth/access-token")
    suspend fun accessToken(@Header("Authorization") authorization: String): AccessTokenResponse
}

data class StoreLoginBody(
    val signature: String,
    @field:Json(name = "signed_data") val signedData: String,
    @field:Json(name = "package_name") val packageName: String,
    val store: String = "google_play_store",
)

data class AccessTokenResponse(
    @field:Json(name = "access_token") val accessToken: String,
)

data class StoreLoginResponse(
    @field:Json(name = "auth_token") val authToken: String,
    @field:Json(name = "external_id") val externalId: String,
    val email: String?,
    val status: String,
)

data class CreateAccountResponse(
    @field:Json(name = "auth_token") val authToken: String,
    @field:Json(name = "external_id") val externalId: String,
    val status: String,
)

data class ValidateTokenResponse(
    val account: AccountResponse,
)

data class AccountResponse(
    val email: String,
    @field:Json(name = "external_id") val externalId: String,
    val entitlements: List<EntitlementResponse>,
)

data class EntitlementResponse(
    val id: String,
    val name: String,
    val product: String,
)

fun List<EntitlementResponse>.toEntitlements(): List<Entitlement> {
    return this.map { Entitlement(it.name, it.product) }
}

data class ResponseError(
    val error: String,
)
