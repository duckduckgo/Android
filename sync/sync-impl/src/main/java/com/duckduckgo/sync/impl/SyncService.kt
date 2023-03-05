/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.sync.impl

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

@ContributesServiceApi(AppScope::class)
interface SyncService {

    @POST("https://dev-sync-use.duckduckgo.com/sync/signup")
    fun signup(
        @Body request: Signup,
    ): Call<AccountCreatedResponse>

    @POST("https://dev-sync-use.duckduckgo.com/sync/logout-device")
    fun logout(
        @Header("Authorization") token: String,
        @Body request: Logout,
    ): Call<Logout>

    @POST("https://dev-sync-use.duckduckgo.com/sync/delete-account")
    fun deleteAccount(
        @Header("Authorization") token: String,
    ): Call<Void>

    @POST("https://dev-sync-use.duckduckgo.com/sync/login")
    fun login(
        @Body request: Login,
    ): Call<LoginResponse>

    @GET("https://dev-sync-use.duckduckgo.com/sync/devices")
    fun getDevices(
        @Header("Authorization") token: String,
    ): Call<DeviceResponse>
}

data class Login(
    val user_id: String,
    val hashed_password: String,
    val device_id: String,
    val device_name: String,
)

data class Signup(
    val user_id: String,
    val hashed_password: String,
    val protected_encryption_key: String,
    val device_id: String,
    val device_name: String,
)

data class Logout(
    val device_id: String,
)

data class AccountCreatedResponse(
    val user_id: String,
    val token: String,
)

data class LoginResponse(
    val token: String,
    val protected_encryption_key: String,
    val devices: List<Device>,
)

data class DeviceResponse(
    val devices: DeviceEntries,
)

data class DeviceEntries(
    val entries: List<Device>,
)

data class Device(
    val device_id: String,
    val device_name: String,
    val jw_iat: String,
)

data class ErrorResponse(
    val code: Int = -1,
    val error: String,
)
