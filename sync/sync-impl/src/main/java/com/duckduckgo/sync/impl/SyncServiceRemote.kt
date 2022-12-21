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
import com.duckduckgo.sync.store.SyncEncryptedStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import timber.log.Timber
import java.lang.IllegalArgumentException

interface SyncApi {
    fun createAccount(
        userID: String,
        hashedPassword: String,
        protectedEncryptionKey: String,
        deviceIds: String,
        deviceName: String,
    )
}

@ContributesBinding(AppScope::class)
class SyncServiceRemote
@Inject
constructor(
    @Named("nonCaching") private val retrofit: Retrofit,
    private val syncService: SyncService,
    private val syncEncryptedStore: SyncEncryptedStore,
) : SyncApi {
    override fun createAccount(
        userID: String,
        hashedPassword: String,
        protectedEncryptionKey: String,
        deviceIds: String,
        deviceName: String,
    ) {
        kotlin
            .runCatching {
                val call =
                    syncService.signup(
                        Signup(
                            userID, hashedPassword, protectedEncryptionKey, deviceIds, deviceName))
                call.execute()
            }
            .onSuccess { response ->
                if (response.isSuccessful) {
                    Timber.i("SYNC signup success ${response.code()} ${response.message()}")
                    syncEncryptedStore.token =
                        response.body()?.token ?: throw IllegalStateException("Empty body")
                } else {
                    response.errorBody()?.let { errorBody ->
                        val converter: Converter<ResponseBody, ErrorResponse> =
                            retrofit.responseBodyConverter(
                                ErrorResponse::class.java, arrayOfNulls(0))
                        val errorResponse = converter.convert(errorBody) ?: throw IllegalArgumentException("Can't parse body")
                        Timber.i("SYNC signup failed ${errorResponse.code} ${errorResponse.error}")
                    } ?: kotlin.run {
                        Timber.i("SYNC signup failed ${response.code()} ${response.message()}")
                    }
                }
            }
            .onFailure { throwable -> Timber.i("SYNC signup failed ${throwable.message}") }
    }
}
