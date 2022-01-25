/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.waitlist.api

import retrofit2.http.*

interface AppTrackingProtectionWaitlistService {

    @POST("https://quack.duckduckgo.com/api/auth/waitlist/apptp/join")
    suspend fun joinWaitlist(): WaitlistResponse

    @GET("https://quack.duckduckgo.com/api/auth/waitlist/apptp/status")
    suspend fun waitlistStatus(): WaitlistStatusResponse

    @FormUrlEncoded
    @POST("https://quack.duckduckgo.com/api/auth/waitlist/apptp/code")
    suspend fun getCode(@Field("token") token: String): AppTPInviteCodeResponse

    @POST("https://quack.duckduckgo.com/api/auth/invites/apptp/redeem")
    suspend fun redeemCode(@Query("code") code: String): AppTPRedeemCodeResponse
}

data class WaitlistResponse(
    val token: String?,
    val timestamp: Int?
)

data class WaitlistStatusResponse(val timestamp: Int)
data class AppTPInviteCodeResponse(val code: String)
data class AppTPRedeemCodeResponse(
    val product: String,
    val status: String,
    val error: String
)

data class AppTPRedeemCodeError(val error: String) {
    companion object {
        const val INVALID = "invalid_invite_code"
        const val ALREADY_REDEEMED = "already_redeemed_invite_code"
    }
}
