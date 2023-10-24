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

package com.duckduckgo.networkprotection.impl.configuration

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

@ContributesServiceApi(AppScope::class)
interface WgVpnControllerService {
    @Headers("Content-Type: application/json")
    @POST("$NETP_ENVIRONMENT_URL/redeem")
    suspend fun redeemCode(@Body code: NetPRedeemCodeRequest): NetPRedeemCodeResponse

    @GET("$NETP_ENVIRONMENT_URL/servers")
    suspend fun getServers(): List<RegisteredServerInfo>

    @Headers("Content-Type: application/json")
    @POST("$NETP_ENVIRONMENT_URL/register")
    suspend fun registerKey(
        @Body registerKeyBody: RegisterKeyBody,
    ): List<EligibleServerInfo>
}

const val NETP_ENVIRONMENT_URL = "https://controller.netp.duckduckgo.com"

data class NetPRedeemCodeRequest(
    val code: String,
)

data class NetPRedeemCodeResponse(
    val token: String,
)

data class NetPRedeemCodeError(val message: String) {
    companion object {
        const val INVALID = "invalid code"
    }
}

data class RegisteredServerInfo(
    val registeredAt: String,
    val server: Server,
)

data class RegisterKeyBody(
    val publicKey: String,
    val server: String = "*",
    val country: String? = null,
    val city: String? = null,
)

data class EligibleServerInfo(
    val publicKey: String, // client public key
    val allowedIPs: List<String>,
    val server: Server,
)

data class Server(
    val name: String,
    val internalIp: String,
    val attributes: Map<String, Any>,
    val publicKey: String,
    val hostnames: List<String>,
    val ips: List<String>,
    val port: Long,
)
