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
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.di.ProtectedVpnControllerService
import com.duckduckgo.networkprotection.impl.di.UnprotectedVpnControllerService
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.net.InetAddress
import javax.inject.Named
import javax.inject.Qualifier
import logcat.logcat
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

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
        context: Context,
        appBuildConfig: AppBuildConfig,
    ): OkHttpClient {
        val network = context.getNonVpnNetwork()

        return okHttpClient.newBuilder()
            .apply {
                if (appBuildConfig.isInternalBuild()) {
                    addNetworkInterceptor(LoggingNetworkInterceptor())
                }
                network?.socketFactory?.let {
                    socketFactory(it)

                    dns(
                        object : Dns {
                            override fun lookup(hostname: String): List<InetAddress> {
                                return try {
                                    network.getAllByName(hostname).toList()
                                } catch (t: Throwable) {
                                    Dns.SYSTEM.lookup(hostname)
                                }
                            }
                        },
                    )
                }
            }
            .build()
    }

    @Provides
    @UnprotectedVpnControllerService
    @SingleInstanceIn(VpnScope::class)
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun providesWgVpnControllerService(
        @Named(value = "api") retrofit: Retrofit,
        @InternalApi customClient: Lazy<OkHttpClient>,
    ): WgVpnControllerService {
        val customRetrofit = retrofit.newBuilder()
            .callFactory { customClient.get().newCall(it) }
            .build()

        return customRetrofit.create(WgVpnControllerService::class.java)
    }

    private fun Context.getNonVpnNetwork(): Network? {
        val connectivityManager = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        connectivityManager?.let { cm ->
            cm.allNetworks.firstOrNull()?.let { network ->
                cm.getNetworkCapabilities(network)?.let { capabilities ->
                    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return network
                    }
                }
            }
        }

        return null
    }
}

private class LoggingNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val connection = chain.connection()

        // Get the IP of the socket used
        val socketAddress = connection?.socket()?.localAddress?.hostAddress

        logcat { "Request to: ${request.url} uses socket: $socketAddress" }

        return chain.proceed(request)
    }
}

@ContributesServiceApi(AppScope::class)
@ProtectedVpnControllerService
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

const val NETP_ENVIRONMENT_URL = "https://controller.netp.duckduckgo.com"

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
