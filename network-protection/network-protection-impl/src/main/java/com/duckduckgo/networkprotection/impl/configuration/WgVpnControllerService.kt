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
import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnSocketProtector
import com.duckduckgo.networkprotection.impl.di.ProtectedVpnControllerService
import com.duckduckgo.networkprotection.impl.di.UnprotectedVpnControllerService
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.security.KeyStore
import java.security.Security
import javax.inject.Named
import javax.inject.Qualifier
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

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
        delegatingSSLSocketFactory: DelegatingSSLSocketFactory,
    ): OkHttpClient {
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm(),
        )
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        check(!(trustManagers.size != 1 || trustManagers[0] !is X509TrustManager)) {
            ("Unexpected default trust managers: ${trustManagers.contentToString()}")
        }
        val trustManager = trustManagers[0] as X509TrustManager

        return okHttpClient.newBuilder()
            .sslSocketFactory(delegatingSSLSocketFactory, trustManager)
            .build()
    }

    @Provides
    @SingleInstanceIn(VpnScope::class)
    fun provideDelegatingSSLSocketFactory(
        socketProtector: Lazy<VpnSocketProtector>,
        @Named("api") okHttpClient: Lazy<OkHttpClient>,
    ): DelegatingSSLSocketFactory {
        return object : DelegatingSSLSocketFactory(okHttpClient.get().sslSocketFactory) {
            override fun configureSocket(sslSocket: SSLSocket): SSLSocket {
                socketProtector.get().protect(sslSocket)
                return sslSocket
            }
        }
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

        // insertProviderAt trick to avoid error during handshakes
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        return customRetrofit.create(WgVpnControllerService::class.java)
    }
}

@ContributesServiceApi(AppScope::class)
@ProtectedVpnControllerService
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

    @GET("$NETP_ENVIRONMENT_URL/locations")
    suspend fun getEligibleLocations(): List<EligibleLocation>
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
