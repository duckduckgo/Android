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
import com.squareup.moshi.Json
import org.json.JSONObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@ContributesServiceApi(AppScope::class)
interface SyncService {

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/signup")
    fun signup(
        @Body request: Signup,
    ): Call<AccountCreatedResponse>

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/logout-device")
    fun logout(
        @Header("Authorization") token: String,
        @Body request: Logout,
    ): Call<Logout>

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/delete-account")
    fun deleteAccount(
        @Header("Authorization") token: String,
    ): Call<Void>

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/login")
    fun login(
        @Body request: Login,
    ): Call<LoginResponse>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/devices")
    fun getDevices(
        @Header("Authorization") token: String,
    ): Call<DeviceResponse>

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/connect")
    fun connect(
        @Header("Authorization") token: String,
        @Body request: Connect,
    ): Call<Void>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/connect/{device_id}")
    fun connectDevice(
        @Path("device_id") deviceId: String,
    ): Call<ConnectKey>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/exchange/{key_id}")
    fun getEncryptedMessage(
        @Path("key_id") keyId: String,
    ): Call<EncryptedMessage>

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/exchange")
    fun sendEncryptedMessage(
        @Body request: EncryptedMessage,
    ): Call<Void>

    @PATCH("$SYNC_PROD_ENVIRONMENT_URL/sync/data")
    fun patch(
        @Header("Authorization") token: String,
        @Body request: JSONObject,
    ): Call<JSONObject>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/bookmarks")
    fun bookmarks(
        @Header("Authorization") token: String,
    ): Call<JSONObject>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/bookmarks")
    fun bookmarksSince(@Header("Authorization") token: String, @Query("since") since: String): Call<JSONObject>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/credentials")
    fun credentials(
        @Header("Authorization") token: String,
    ): Call<JSONObject>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/credentials")
    fun credentialsSince(@Header("Authorization") token: String, @Query("since") since: String): Call<JSONObject>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/settings")
    fun settings(
        @Header("Authorization") token: String,
    ): Call<JSONObject>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/settings")
    fun settingsSince(@Header("Authorization") token: String, @Query("since") since: String): Call<JSONObject>

    companion object {
        const val SYNC_PROD_ENVIRONMENT_URL = "https://sync.duckduckgo.com"
        const val SYNC_DEV_ENVIRONMENT_URL = "https://dev-sync-use.duckduckgo.com"
    }
}

data class Login(
    @field:Json(name = "user_id") val userId: String,
    @field:Json(name = "hashed_password") val hashedPassword: String,
    @field:Json(name = "device_id") val deviceId: String,
    @field:Json(name = "device_name") val deviceName: String,
    @field:Json(name = "device_type") val deviceType: String,
)

data class Signup(
    @field:Json(name = "user_id") val userId: String,
    @field:Json(name = "hashed_password") val hashedPassword: String,
    @field:Json(name = "protected_encryption_key") val protectedEncryptionKey: String,
    @field:Json(name = "device_id") val deviceId: String,
    @field:Json(name = "device_name") val deviceName: String,
    @field:Json(name = "device_type") val deviceType: String,
)

data class Logout(
    @field:Json(name = "device_id") val deviceId: String,
)

data class ConnectKey(
    @field:Json(name = "encrypted_recovery_key") val encryptedRecoveryKey: String,
)

data class EncryptedMessage(
    @field:Json(name = "key_id") val keyId: String,
    @field:Json(name = "encrypted_message") val encryptedMessage: String,
)

data class Connect(
    @field:Json(name = "device_id") val deviceId: String,
    @field:Json(name = "encrypted_recovery_key") val encryptedRecoveryKey: String,
)

data class AccountCreatedResponse(
    @field:Json(name = "user_id") val userId: String,
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
    @field:Json(name = "id") val deviceId: String,
    @field:Json(name = "name") val deviceName: String,
    @field:Json(name = "type") val deviceType: String?,
    @field:Json(name = "jw_iat") val jwIat: String,
)

data class ErrorResponse(
    val code: Int = -1,
    val error: String,
)

@Suppress("ktlint:standard:class-naming")
enum class API_CODE(val code: Int) {
    INVALID_LOGIN_CREDENTIALS(401),
    NOT_MODIFIED(304),
    COUNT_LIMIT(409),
    CONTENT_TOO_LARGE(413),
    VALIDATION_ERROR(400),
    TOO_MANY_REQUESTS_1(429),
    TOO_MANY_REQUESTS_2(418),
    NOT_FOUND(404),
    GONE(410),
}
