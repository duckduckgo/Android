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

package com.duckduckgo.autofill.impl.email.service

import com.duckduckgo.anvil.annotations.ContributesNonCachingServiceApi
import com.duckduckgo.di.scopes.AppScope
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query

@ContributesNonCachingServiceApi(AppScope::class)
interface DuckAddressStatusManagementService {

    @GET("$BASE_URL/api/email/addresses")
    suspend fun getActivationStatus(
        @Header("Authorization") authorization: String,
        @Query("address") duckAddress: String,
    ): DuckAddressGetStatusResponse

    @PUT("$BASE_URL/api/email/addresses")
    suspend fun setActivationStatus(
        @Header("Authorization") authorization: String,
        @Query("address") duckAddress: String,
        @Query("active") isActive: Boolean,
    ): DuckAddressGetStatusResponse

    data class DuckAddressGetStatusResponse(
        val active: Boolean,
    )

    companion object {
        private const val BASE_URL = "https://quack.duckduckgo.com"
    }
}

data class EmailAlias(val address: String)
