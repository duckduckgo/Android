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
import retrofit2.Response

interface SyncApi {
    fun createAccount(
        userID: String,
        primaryKey: String,
        secretKey: String,
        hashedPassword: String,
        protectedEncryptionKey: String,
        deviceId: String,
        deviceName: String,
    ): Result<AccountCreatedResponse>
}

@ContributesBinding(AppScope::class)
class SyncServiceRemote
@Inject
constructor(
    private val syncService: SyncService,
) : SyncApi {
    override fun createAccount(
        userID: String,
        primaryKey: String,
        secretKey: String,
        hashedPassword: String,
        protectedEncryptionKey: String,
        deviceId: String,
        deviceName: String,
    ): Result<AccountCreatedResponse> {
        val response =
            runCatching {
                val call = syncService.signup(
                    Signup(
                        user_id = userID,
                        hashed_password = hashedPassword,
                        protected_encryption_key = protectedEncryptionKey,
                        device_id = deviceId,
                        device_name = deviceName,
                    ),
                )
                call.execute()
            }.getOrElse { throwable ->
                return Result.Error(reason = throwable.message.toString())
            }

        return onSuccess(response) {
            val token = response.body()?.token.takeUnless { it.isNullOrEmpty() } ?: throw IllegalStateException("Empty body")
            val userId = response.body()?.user_id.takeUnless { it.isNullOrEmpty() } ?: throw IllegalStateException("Empty body")
            Result.Success(
                AccountCreatedResponse(
                    token = token,
                    user_id = userId,
                ),
            )
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
            return Result.Error(reason = response.message())
        }
    }

    class Adapters {
        companion object {
            private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val errorResponseAdapter: JsonAdapter<ErrorResponse> =
                moshi.adapter(ErrorResponse::class.java).lenient()
        }
    }
}
