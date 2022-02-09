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

package com.duckduckgo.macos_impl.waitlist.api

import retrofit2.http.*

interface MacOsWaitlistService {

    @POST("https://quackdev.duckduckgo.com/api/auth/waitlist/macosbrowser/join")
    suspend fun joinWaitlist(): MacOsWaitlistResponse

    @GET("https://quackdev.duckduckgo.com/api/auth/waitlist/macosbrowser/status")
    suspend fun waitlistStatus(): MacOsWaitlistStatusResponse

    @FormUrlEncoded
    @POST("https://quackdev.duckduckgo.com/api/auth/waitlist/macosbrowser/code")
    suspend fun getCode(@Field("token") token: String): MacOsInviteCodeResponse
}

data class MacOsWaitlistResponse(val token: String?, val timestamp: Int?)
data class MacOsWaitlistStatusResponse(val timestamp: Int)
data class MacOsInviteCodeResponse(val code: String)

object Url {
    const val API = "https://quack.duckduckgo.com"
}
