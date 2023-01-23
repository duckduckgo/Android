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

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface SyncService {

    @POST("https://dev-sync-use.duckduckgo.com/sync/signup")
    fun signup(
        @Body request: Signup,
    ): Call<AccountCreatedResponse>
}

data class Signup(
    val user_id: String,
    val hashed_password: String,
    val protected_encryption_key: String,
    val device_id: String,
    val device_name: String,
)

data class AccountCreatedResponse(
    val user_id: String,
    val token: String,
)

data class ErrorResponse(
    val code: Int = -1,
    val error: String,
)
