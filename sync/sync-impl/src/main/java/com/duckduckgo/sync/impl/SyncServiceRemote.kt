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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat
import org.json.JSONObject
import retrofit2.Response
import javax.inject.*

interface SyncApi {
    fun createAccount(
        userID: String,
        hashedPassword: String,
        protectedEncryptionKey: String,
        deviceId: String,
        deviceName: String,
        deviceType: String,
    ): Result<AccountCreatedResponse>

    fun login(
        userID: String,
        hashedPassword: String,
        deviceId: String,
        deviceName: String,
        deviceType: String,
    ): Result<LoginResponse>

    fun logout(
        token: String,
        deviceId: String,
    ): Result<Logout>

    fun connect(
        token: String,
        deviceId: String,
        publicKey: String,
    ): Result<Boolean>

    fun connectDevice(
        deviceId: String,
    ): Result<String>

    fun getEncryptedMessage(keyId: String): Result<String>

    fun sendEncryptedMessage(
        keyId: String,
        encryptedSecrets: String,
    ): Result<Boolean>

    fun deleteAccount(token: String): Result<Boolean>

    fun getDevices(token: String): Result<List<Device>>

    fun patch(
        token: String,
        updates: JSONObject,
    ): Result<JSONObject?>

    fun getBookmarks(
        token: String,
        since: String,
    ): Result<JSONObject>

    fun getCredentials(
        token: String,
        since: String,
    ): Result<JSONObject>

    fun getSettings(
        token: String,
        since: String,
    ): Result<JSONObject>
}

@ContributesBinding(AppScope::class)
class SyncServiceRemote @Inject constructor(
    private val syncService: SyncService,
    private val syncStore: SyncStore,
) : SyncApi {
    override fun createAccount(
        userID: String,
        hashedPassword: String,
        protectedEncryptionKey: String,
        deviceId: String,
        deviceName: String,
        deviceType: String,
    ): Result<AccountCreatedResponse> {
        val response = runCatching {
            val call = syncService.signup(
                Signup(
                    userId = userID,
                    hashedPassword = hashedPassword,
                    protectedEncryptionKey = protectedEncryptionKey,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    deviceType = deviceType,
                ),
            )
            call.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            val token = response.body()?.token.takeUnless { it.isNullOrEmpty() }
                ?: return@onSuccess Result.Error(reason = "CreateAccount: Token not found in Body")
            val userId = response.body()?.userId.takeUnless { it.isNullOrEmpty() }
                ?: return@onSuccess Result.Error(reason = "CreateAccount: userId missing in Body")
            Result.Success(
                AccountCreatedResponse(
                    token = token,
                    userId = userId,
                ),
            )
        }
    }

    override fun logout(
        token: String,
        deviceId: String,
    ): Result<Logout> {
        val response = runCatching {
            val logoutCall = syncService.logout("Bearer $token", Logout(deviceId))
            logoutCall.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            val deviceIdResponse =
                response.body()?.deviceId.takeUnless { it.isNullOrEmpty() } ?: return@onSuccess Result.Error(reason = "Logout: empty body")
            Result.Success(Logout(deviceIdResponse))
        }
    }

    override fun getDevices(token: String): Result<List<Device>> {
        val response = runCatching {
            val logoutCall = syncService.getDevices("Bearer $token")
            logoutCall.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            val devices = response.body()?.devices?.entries ?: return@onSuccess Result.Error(reason = "getDevices: empty body")
            Result.Success(devices)
        }
    }

    override fun connect(
        token: String,
        deviceId: String,
        publicKey: String,
    ): Result<Boolean> {
        val response = runCatching {
            val logoutCall = syncService.connect("Bearer $token", Connect(deviceId = deviceId, encryptedRecoveryKey = publicKey))
            logoutCall.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            Result.Success(true)
        }
    }

    override fun connectDevice(deviceId: String): Result<String> {
        val response = runCatching {
            val logoutCall = syncService.connectDevice(deviceId)
            logoutCall.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            val sealed = response.body()?.encryptedRecoveryKey.takeUnless { it.isNullOrEmpty() }
                ?: return@onSuccess Result.Error(reason = "ConnectDevice: empty body")
            Result.Success(sealed)
        }
    }

    override fun getEncryptedMessage(keyId: String): Result<String> {
        logcat(VERBOSE) { "Sync-exchange: Looking for exchange for keyId: $keyId" }
        val response = runCatching {
            val request = syncService.getEncryptedMessage(keyId)
            request.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            val sealed = response.body()?.encryptedMessage.takeUnless { it.isNullOrEmpty() }
                ?: return@onSuccess Result.Error(reason = "InvitationFlow: empty body")
            Result.Success(sealed)
        }
    }

    override fun sendEncryptedMessage(
        keyId: String,
        encryptedSecrets: String,
    ): Result<Boolean> {
        val response = runCatching {
            val shareRecoveryKeyRequest = EncryptedMessage(
                keyId = keyId,
                encryptedMessage = encryptedSecrets,
            )
            val sendSecretCall = syncService.sendEncryptedMessage(shareRecoveryKeyRequest)
            sendSecretCall.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            Result.Success(true)
        }
    }

    override fun deleteAccount(token: String): Result<Boolean> {
        val response = runCatching {
            val deleteAccountCall = syncService.deleteAccount("Bearer $token")
            deleteAccountCall.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            Result.Success(true)
        }
    }

    override fun login(
        userID: String,
        hashedPassword: String,
        deviceId: String,
        deviceName: String,
        deviceType: String,
    ): Result<LoginResponse> {
        val response = runCatching {
            val call = syncService.login(
                Login(
                    userId = userID,
                    hashedPassword = hashedPassword,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    deviceType = deviceType,
                ),
            )
            call.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.toString())
        }

        return onSuccess(response) {
            val token = response.body()?.token ?: return@onSuccess Result.Error(reason = "Login: empty token in Body")
            val protectedEncryptionKey =
                response.body()?.protected_encryption_key ?: return@onSuccess Result.Error(reason = "Login: empty PEK in Body")

            Result.Success(
                LoginResponse(
                    token = token,
                    protected_encryption_key = protectedEncryptionKey,
                    devices = emptyList(),
                ),
            )
        }
    }

    override fun patch(
        token: String,
        updates: JSONObject,
    ): Result<JSONObject> {
        logcat(INFO) { "Sync-service: patch request $updates" }

        val response = runCatching {
            val patchCall = syncService.patch("Bearer $token", updates)
            patchCall.execute()
        }.getOrElse { throwable ->
            logcat(INFO) { "Sync-service: error ${throwable.localizedMessage}" }
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            logcat(INFO) { "Sync-service: patch response: $it" }
            val data = response.body() ?: return@onSuccess Result.Error(reason = "Patch: empty Body")
            Result.Success(data)
        }
    }

    override fun getBookmarks(
        token: String,
        since: String,
    ): Result<JSONObject> {
        logcat(INFO) { "Sync-service: get bookmarks since servertime $since" }
        val response = runCatching {
            val patchCall = if (since.isNotEmpty()) {
                syncService.bookmarksSince("Bearer $token", since)
            } else {
                syncService.bookmarks("Bearer $token")
            }
            patchCall.execute()
        }.getOrElse { throwable ->
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            val data = response.body() ?: return@onSuccess Result.Error(reason = "GetBookmarks: empty body")
            Result.Success(data)
        }
    }

    override fun getCredentials(
        token: String,
        since: String,
    ): Result<JSONObject> {
        logcat(INFO) { "Sync-service: get credentials since servertime $since" }
        val response = runCatching {
            val patchCall = if (since.isNotEmpty()) {
                syncService.credentialsSince("Bearer $token", since)
            } else {
                syncService.credentials("Bearer $token")
            }
            patchCall.execute()
        }.getOrElse { throwable ->
            logcat(INFO) { "Sync-service: patch response: ${throwable.localizedMessage}" }
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            logcat(INFO) { "Sync-service: get credentials response: $it" }
            val data = response.body() ?: return@onSuccess Result.Error(reason = "GetCredentials: empty body")
            Result.Success(data)
        }
    }

    override fun getSettings(
        token: String,
        since: String,
    ): Result<JSONObject> {
        logcat(INFO) { "Sync-settings: get settings since servertime $since" }
        val response = runCatching {
            val patchCall = if (since.isNotEmpty()) {
                syncService.settingsSince("Bearer $token", since)
            } else {
                syncService.settings("Bearer $token")
            }
            patchCall.execute()
        }.getOrElse { throwable ->
            logcat(INFO) { "Sync-service: patch response: ${throwable.localizedMessage}" }
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            logcat(INFO) { "Sync-service: get settings response: $it" }
            val data = response.body() ?: return@onSuccess Result.Error(reason = "GetSettings: empty body")
            Result.Success(data)
        }
    }

    private fun <T, R> onSuccess(
        response: Response<T?>,
        onSuccess: (T?) -> Result<R>,
    ): Result<R> {
        runCatching {
            if (response.isSuccessful) {
                return onSuccess(response.body())
            } else {
                val error = response.errorBody()?.let { errorBody ->
                    val error = Adapters.errorResponseAdapter.fromJson(errorBody.string())
                        ?: ErrorResponse(error = "Can't parse Error body")
                    val code = if (error.code == -1) response.code() else error.code
                    Result.Error(code, error.error)
                } ?: Result.Error(code = response.code(), reason = response.message().toString())
                error.removeKeysIfInvalid()
                return error
            }
        }.getOrElse {
            val result = Result.Error(response.code(), reason = response.message())
            result.removeKeysIfInvalid()
            return result
        }
    }

    private fun Result.Error.removeKeysIfInvalid() {
        if (code == API_CODE.INVALID_LOGIN_CREDENTIALS.code) {
            syncStore.clearAll()
        }
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val errorResponseAdapter: JsonAdapter<ErrorResponse> =
                moshi.adapter(ErrorResponse::class.java).lenient()
        }
    }
}
