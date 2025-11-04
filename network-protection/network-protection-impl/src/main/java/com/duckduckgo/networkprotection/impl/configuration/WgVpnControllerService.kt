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

import android.annotation.SuppressLint
import com.duckduckgo.di.scopes.VpnScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import javax.inject.Named
import javax.inject.Qualifier

@Module
@ContributesTo(scope = VpnScope::class)
object WgVpnControllerServiceModule {

    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    private annotation class InternalApi

    @Provides
    @InternalApi
    @SingleInstanceIn(VpnScope::class)
    fun provideInternalCustomHttpClient(
        @Named("api") okHttpClient: OkHttpClient,
        vpnLocalDns: VpnLocalDns,
    ): OkHttpClient {
        return okHttpClient.newBuilder()
            .dns(vpnLocalDns)
            .build()
    }

    @Provides
    @SingleInstanceIn(VpnScope::class)
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun providesWgVpnControllerService(
        @Named(value = "api") retrofit: Retrofit,
        @InternalApi customClient: Lazy<OkHttpClient>,
    ): WgVpnControllerService {
        return retrofit.newBuilder()
            .callFactory { customClient.get().newCall(it) }
            .build()
            .create(WgVpnControllerService::class.java)
    }
}

// We need to provide a custom OkHttp, that's why we don't use the [ContributesServiceApi] annotation
interface WgVpnControllerService {
    @GET("$NETP_ENVIRONMENT_URL/servers")
    @AuthRequired
    suspend fun getServers(): List<RegisteredServerInfo>

    @GET("$NETP_ENVIRONMENT_URL/servers/{serverName}/status")
    suspend fun getServerStatus(
        @Path("serverName") serverName: String,
    ): ServerStatus

    @Headers("Content-Type: application/json")
    @AuthRequired
    @POST("$NETP_ENVIRONMENT_URL/register")
    suspend fun registerKey(
        @Body registerKeyBody: RegisterKeyBody,
    ): List<EligibleServerInfo>

    @GET("$NETP_ENVIRONMENT_URL/locations")
    @AuthRequired
    suspend fun getEligibleLocations(): List<EligibleLocation>
}

const val NETP_ENVIRONMENT_URL = "https://staging.netp.duckduckgo.com"

data class ServerStatus(
    val shouldMigrate: Boolean,
)

data class RegisteredServerInfo(
    val registeredAt: String,
    val server: Server,
)

data class RegisterKeyBody(
    val publicKey: String,
    val server: String = "*",
    val country: String? = null,
    val city: String? = null,
    val mode: String? = null,
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

data class EligibleLocation(
    val country: String,
    val cities: List<EligibleCity>,
)

data class EligibleCity(
    val name: String,
)

/**
 * This annotation is used in interceptors to be able to intercept the annotated service calls
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthRequired
