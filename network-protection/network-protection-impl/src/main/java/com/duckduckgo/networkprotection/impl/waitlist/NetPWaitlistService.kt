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

package com.duckduckgo.networkprotection.impl.waitlist

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

@ContributesServiceApi(AppScope::class)
interface NetPWaitlistService {
    @POST("$API/api/auth/waitlist/networkprotection/join")
    suspend fun joinWaitlist(): WaitlistResponse

    @GET("$API/api/auth/waitlist/networkprotection/status")
    suspend fun waitlistStatus(): WaitlistStatusResponse

    @FormUrlEncoded
    @POST("$API/api/auth/waitlist/networkprotection/code")
    suspend fun getCode(@Field("token")token: String): InviteCodeResponse

    data class WaitlistResponse(val token: String?, val timestamp: Int?)
    data class WaitlistStatusResponse(val timestamp: Int)
    data class InviteCodeResponse(val code: String)
}

private const val API = "https://quack.duckduckgo.com"
