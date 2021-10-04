/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email.api

import retrofit2.http.*

interface EmailService {
    @POST("https://quack.duckduckgo.com/api/email/addresses")
    suspend fun newAlias(@Header("Authorization") authorization: String): EmailAlias

    @POST("https://quack.duckduckgo.com/api/auth/waitlist/join")
    suspend fun joinWaitlist(): WaitlistResponse

    @GET("https://quack.duckduckgo.com/api/auth/waitlist/status")
    suspend fun waitlistStatus(): WaitlistStatusResponse

    @FormUrlEncoded
    @POST("https://quack.duckduckgo.com/api/auth/waitlist/code")
    suspend fun getCode(@Field("token") token: String): EmailInviteCodeResponse
}

data class EmailAlias(val address: String)
data class WaitlistResponse(val token: String?, val timestamp: Int?)
data class WaitlistStatusResponse(val timestamp: Int)
data class EmailInviteCodeResponse(val code: String)
