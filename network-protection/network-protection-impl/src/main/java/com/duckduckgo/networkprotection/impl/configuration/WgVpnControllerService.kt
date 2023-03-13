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
import com.duckduckgo.di.scopes.VpnScope
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

@ContributesServiceApi(VpnScope::class)
interface WgVpnControllerService {
    @GET("https://on-dev.goduckgo.com/servers")
    suspend fun getServers(): List<RegisteredServerInfo>

    @Headers("Content-Type: application/json")
    @POST("https://on-dev.goduckgo.com/register")
    suspend fun registerKey(
        @Body registerKeyBody: RegisterKeyBody,
    ): List<EligibleServerInfo>
}

data class RegisteredServerInfo(
    val registeredAt: String,
    val server: Server,
)

data class RegisterKeyBody(
    val publicKey: String,
    val server: String = "*",
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
