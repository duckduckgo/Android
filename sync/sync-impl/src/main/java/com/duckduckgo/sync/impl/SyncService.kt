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
import okhttp3.RequestBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @DELETE("$SYNC_PROD_ENVIRONMENT_URL/sync/ai_chats")
    fun deleteAiChats(
        @Header("Authorization") token: String,
        @Query("until") until: String,
    ): Call<JSONObject>

    @PATCH("$SYNC_PROD_ENVIRONMENT_URL/sync/data")
    fun patchData(
        @Header("Authorization") token: String,
        @Body body: JSONObject,
    ): Call<JSONObject>

    @PATCH("$SYNC_PROD_ENVIRONMENT_URL/sync/ai_chats")
    fun patchChats(
        @Header("Authorization") token: String,
        @Body body: RequestBody,
        @Query("since") since: String? = null,
    ): Call<JSONObject>

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/token/rescope")
    fun rescopeToken(
        @Header("Authorization") token: String,
        @Body request: TokenRescopeRequest,
    ): Call<TokenRescopeResponse>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/keys")
    fun getProtectedKeys(
        @Header("Authorization") token: String,
    ): Call<ProtectedKeysResponse>

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/keys/purpose/{purpose}/set-if-absent")
    fun setProtectedKeyIfAbsent(
        @Header("Authorization") token: String,
        @Path("purpose") purpose: String,
        @Body request: SetProtectedKeyIfAbsentRequest,
    ): Call<Void>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/access-credentials")
    fun getAccessCredentials(
        @Header("Authorization") token: String,
    ): Call<AccessCredentialsResponse>

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/access-credentials/{id}")
    fun createAccessCredential(
        @Header("Authorization") token: String,
        @Path("id") credentialId: String,
        @Body request: CreateAccessCredentialRequest,
    ): Call<Void>

    // ---- Exchange v2 relay (no Authorization — anonymous relay) ----

    @PUT("$SYNC_PROD_ENVIRONMENT_URL/sync/v2/exchange/{channelId}")
    fun createExchangeChannel(
        @Path("channelId") channelId: String,
        @Body body: ExchangeChannelCreateRequest,
    ): Call<Void>

    @POST("$SYNC_PROD_ENVIRONMENT_URL/sync/v2/exchange/{channelId}/messages")
    fun postExchangeMessages(
        @Path("channelId") channelId: String,
        @Body body: ExchangeMessagesRequest,
    ): Call<Void>

    @GET("$SYNC_PROD_ENVIRONMENT_URL/sync/v2/exchange/{channelId}/messages")
    fun pollExchangeMessages(
        @Path("channelId") channelId: String,
        @Query("after") after: Int,
    ): Call<ExchangeMessagesResponse>

    @DELETE("$SYNC_PROD_ENVIRONMENT_URL/sync/v2/exchange/{channelId}")
    fun deleteExchangeChannel(
        @Path("channelId") channelId: String,
    ): Call<Void>

    companion object {
        const val SYNC_PROD_ENVIRONMENT_URL = "https://sync.duckduckgo.com"
        const val SYNC_DEV_ENVIRONMENT_URL = "https://sync-staging.duckduckgo.com"
    }
}

data class Login(
    @field:Json(name = "user_id") val userId: String,
    @field:Json(name = "hashed_password") val hashedPassword: String,
    @field:Json(name = "device_id") val deviceId: String,
    @field:Json(name = "device_name") val deviceName: String,
    @field:Json(name = "device_type") val deviceType: String,
    @field:Json(name = "scope") val scope: String? = null,
)

data class Signup(
    @field:Json(name = "user_id") val userId: String,
    @field:Json(name = "hashed_password") val hashedPassword: String,
    @field:Json(name = "protected_encryption_key") val protectedEncryptionKey: String,
    @field:Json(name = "device_id") val deviceId: String,
    @field:Json(name = "device_name") val deviceName: String,
    @field:Json(name = "device_type") val deviceType: String,
    @field:Json(name = "credential_id") val credentialId: String? = null,
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
    // Absent when the matched access credential is 3party-restricted; populated for ddg/legacy
    // credentials. Callers on the ddg path must null-check before use.
    val protected_encryption_key: String? = null,
    val devices: List<Device>,
    @field:Json(name = "access_credentials") val accessCredentials: List<AccessCredentialEntry>? = null,
    val keys: List<ProtectedKeyEntry>? = null,
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

data class TokenRescopeRequest(
    val scope: String,
)

data class TokenRescopeResponse(
    val token: String,
)

/** Response from GET /sync/keys — the account's protected keys for all purposes. */
data class ProtectedKeysResponse(
    val keys: List<ProtectedKeyEntry>,
)

/** Body for POST /sync/keys/purpose/{purpose}/set-if-absent — adds a protected key only if no key exists for that purpose. */
data class SetProtectedKeyIfAbsentRequest(
    val key: ProtectedKeyEntry,
)

data class AccessCredentialsResponse(
    @field:Json(name = "access_credentials") val accessCredentials: List<AccessCredentialEntry>,
)

/**
 * One entry in GET /sync/access-credentials. Each entry represents an access credential the
 * account holds (id "ddg" for the native credential, "3party" for the scoped-access one shared
 * with browser surfaces). `encryptedCredential` is the credential's secret encrypted with the
 * companion credential's MEK (e.g. for id="3party", encrypted with the DDG MEK).
 *
 */
data class AccessCredentialEntry(
    val id: String,
    val scope: String? = null,
    @field:Json(name = "encrypted_3party_credential") val encryptedCredential: String? = null,
)

data class CreateAccessCredentialRequest(
    @field:Json(name = "hashed_password") val hashedPassword: String,
    @field:Json(name = "credential_hashed_password") val credentialHashedPassword: String,
    @field:Json(name = "protected_encryption_key") val protectedEncryptionKey: String? = null,
    @field:Json(name = "encrypted_3party_credential") val encrypted3partyCredential: String? = null,
    val keys: List<ProtectedKeyEntry>? = null,
)

/**
 * A protected RSA keypair stored against the account for a specific [purpose] (e.g. "ai_chats").
 * [encryptedPrivateKey] is wrapped with the credential identified by [encryptedWith] ("ddg" or
 * "3party"); each purpose can have one entry per credential. [publicKey] is sent in JWK form so
 * clients without the private key can still use it for asymmetric encryption.
 */
data class ProtectedKeyEntry(
    val kid: String,
    val purpose: String,
    @field:Json(name = "encrypted_with") val encryptedWith: String,
    @field:Json(name = "encrypted_private_key") val encryptedPrivateKey: String,
    @field:Json(name = "public_key") val publicKey: RsaJwk? = null,
)

// ---- Exchange v2 relay envelope models ----

/**
 * Outer envelope sent on the v2 exchange relay. The [version] field is unencrypted and used
 * for protocol version negotiation. [payload] is a JWE compact string (RSA-OAEP-256 +
 * A256GCM) containing the actual message JSON. See Transport TD (Asana 1214486492252757).
 */
data class ExchangeEnvelope(
    val version: String,
    val payload: String,
)

/** Body for PUT /sync/v2/exchange/{channelId} — opens the channel. Empty per spec. */
class ExchangeChannelCreateRequest

/** Body for POST /sync/v2/exchange/{channelId}/messages — batch send. */
data class ExchangeMessagesRequest(
    val messages: List<ExchangeEnvelope>,
)

/** Response from GET /sync/v2/exchange/{channelId}/messages?after={seq}. */
data class ExchangeMessagesResponse(
    val messages: List<ExchangeMessageEntry>,
)

/** Server-assigned [seq] plus the envelope contents. */
data class ExchangeMessageEntry(
    val seq: Int,
    val version: String,
    val payload: String,
)

/** RSA-OAEP-256 public key in JWK format (RFC 7517) for sync protected key entries. */
data class RsaJwk(
    val alg: String = "RSA-OAEP-256",
    val e: String,
    val ext: Boolean = true,
    @field:Json(name = "key_ops") val keyOps: List<String> = listOf("encrypt"),
    val kty: String = "RSA",
    val n: String,
    val use: String = "enc",
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
