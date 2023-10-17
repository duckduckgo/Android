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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Named
import okhttp3.Dns
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

// TODO this is a temporary solution
// see https://app.asana.com/0/488551667048375/1205732562346501/f
@Module
@ContributesTo(scope = AppScope::class)
object WgVpnControllerServiceModule {
    @Provides
    fun providesWgVpnControllerService(
        @Named(value = "api") retrofit: Retrofit,
        @Named("api") okHttpClient: Lazy<OkHttpClient>,
    ): WgVpnControllerService {
        val customRetrofit = retrofit.newBuilder()
            .callFactory {
                okHttpClient.get().newBuilder()
                    .dns(DnsOverride())
                    .build()
                    .newCall(it)
            }
            .build()

        return customRetrofit.create(WgVpnControllerService::class.java)
    }
}

// we don't use ContributesServiceApi for now because we need a custom okhttp client that overrides DNS
// see https://app.asana.com/0/488551667048375/1205732562346501/f
// @ContributesServiceApi(AppScope::class)
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

private class DnsOverride : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            InetAddress.getAllByName(hostname).toList()
        } catch (e: NullPointerException) {
            // This is just copied from https://github.com/square/okhttp/blob/master/okhttp/src/jvmMain/kotlin/okhttp3/Dns.kt
            throw UnknownHostException("Broken system behaviour for dns lookup of $hostname").apply {
                initCause(e)
            }
        } catch (t: UnknownHostException) {
            // presumably the crash seeing in https://app.asana.com/0/488551667048375/1205732562346501/f. Resolve only IPv4
            Inet4Address.getAllByName(hostname).toList()
        }
    }
}
