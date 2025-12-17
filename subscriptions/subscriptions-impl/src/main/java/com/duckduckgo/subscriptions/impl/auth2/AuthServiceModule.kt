/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.auth2

import android.annotation.SuppressLint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import okhttp3.Dns
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Provider

@Module
@ContributesTo(AppScope::class)
object AuthServiceModule {
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAuthService(
        @Named("nonCaching") okHttpClient: Provider<OkHttpClient>,
        @Named("nonCaching") retrofit: Retrofit,
        // This is to force the auth service calls to go outside the VPN
        // https://app.asana.com/1/137249556945/project/488551667048375/task/1212071195929229?focus=true
        dns: Provider<Dns>,
    ): AuthService {
        val okHttpClientWithoutRedirects = lazy {
            okHttpClient.get().newBuilder()
                .followRedirects(false)
                .dns(dns.get())
                .build()
        }

        return retrofit.newBuilder()
            .callFactory { okHttpClientWithoutRedirects.value.newCall(it) }
            .build()
            .create(AuthService::class.java)
    }
}
