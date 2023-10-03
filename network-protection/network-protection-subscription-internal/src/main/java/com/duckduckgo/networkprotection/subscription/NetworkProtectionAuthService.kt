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

package com.duckduckgo.networkprotection.subscription

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

@ContributesServiceApi(AppScope::class)
interface NetworkProtectionAuthService {
    @Headers("Content-Type: application/json")
    @POST("https://staging1.netp.duckduckgo.com/authorize")
    suspend fun authorize(@Body pat: NetPAuthorizeRequest): NetPAuthorizeResponse
}

data class NetPAuthorizeRequest(
    val token: String,
)

data class NetPAuthorizeResponse(
    val token: String,
)
