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
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import org.json.JSONObject
import retrofit2.Response
import timber.log.Timber

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
}

@ContributesBinding(AppScope::class)
class SyncServiceRemote @Inject constructor(private val syncService: SyncService) : SyncApi {
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
            val token = response.body()?.token.takeUnless { it.isNullOrEmpty() } ?: throw IllegalStateException("Token not found")
            val userId = response.body()?.userId.takeUnless { it.isNullOrEmpty() } ?: throw IllegalStateException("userId missing")
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
            val deviceIdResponse = response.body()?.deviceId.takeUnless { it.isNullOrEmpty() } ?: throw IllegalStateException("Token not found")
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
            val devices = response.body()?.devices?.entries ?: throw IllegalStateException("Token not found")
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
            val sealed = response.body()?.encryptedRecoveryKey.takeUnless { it.isNullOrEmpty() } ?: throw IllegalStateException("Token not found")
            Result.Success(sealed)
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
            val token = response.body()?.token ?: throw IllegalStateException("Empty token")
            val protectedEncryptionKey = response.body()?.protected_encryption_key ?: throw IllegalStateException("Empty PEK")

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
        Timber.i("Sync-service: patch request $updates")
        val response = runCatching {
            val patchCall = syncService.patch("Bearer $token", updates)
            patchCall.execute()
        }.getOrElse { throwable ->
            Timber.i("Sync-service: error ${throwable.localizedMessage}")
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            Timber.i("Sync-service: patch response: $it")
            val data = response.body() ?: throw IllegalStateException("Sync-Feature: get data not parsed")
            Result.Success(data)
        }
    }

    override fun getBookmarks(
        token: String,
        since: String,
    ): Result<JSONObject> {
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
            val data = response.body() ?: throw IllegalStateException("Sync-Feature: get data not parsed")
            Result.Success(data)
        }
    }

    override fun getCredentials(
        token: String,
        since: String,
    ): Result<JSONObject> {
        Timber.i("Sync-service: get credentials since servertime $since")
        val response = runCatching {
            val patchCall = if (since.isNotEmpty()) {
                syncService.credentialsSince("Bearer $token", since)
            } else {
                syncService.credentials("Bearer $token")
            }
            patchCall.execute()
        }.getOrElse { throwable ->
            Timber.i("Sync-service: patch response: ${throwable.localizedMessage}")
            return Result.Error(reason = throwable.message.toString())
        }

        return onSuccess(response) {
            Timber.i("Sync-service: get credentials response: $it")
            val data = response.body() ?: throw IllegalStateException("Sync-Feature: get data not parsed")
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
                return response.errorBody()?.let { errorBody ->
                    val error = Adapters.errorResponseAdapter.fromJson(errorBody.string()) ?: throw IllegalArgumentException("Can't parse body")
                    val code = if (error.code == -1) response.code() else error.code
                    Result.Error(code, error.error)
                } ?: Result.Error(code = response.code(), reason = response.message().toString())
            }
        }.getOrElse {
            return Result.Error(response.code(), reason = response.message())
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
