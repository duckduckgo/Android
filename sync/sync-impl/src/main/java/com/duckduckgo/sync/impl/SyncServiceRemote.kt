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
import javax.inject.Inject
import javax.inject.Named
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit

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
    @Named("nonCaching") private val retrofit: Retrofit,
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
                val call =
                    syncService.signup(
                        Signup(
                            userID,
                            hashedPassword,
                            protectedEncryptionKey,
                            deviceId,
                            deviceName,
                        ),
                    )
                call.execute()
            }.getOrElse { throwable ->
                return Result.Error(reason = throwable.message.toString())
            }

        return onSuccess(response) {
            val token = response.body()?.token ?: throw IllegalStateException("Empty body")
            val userId = response.body()?.user_id ?: throw IllegalStateException("Empty body")
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
                    val converter: Converter<ResponseBody, ErrorResponse> =
                        retrofit.responseBodyConverter(
                            ErrorResponse::class.java,
                            arrayOfNulls(0),
                        )
                    val errorResponse =
                        converter.convert(errorBody)
                            ?: throw IllegalArgumentException("Can't parse body")
                    Result.Error(errorResponse.code, errorResponse.error)
                }
                    ?: Result.Error(code = response.code(), reason = response.code().toString())
            }
        }.getOrElse {
            return Result.Error(reason = response.message())
        }
    }
}
