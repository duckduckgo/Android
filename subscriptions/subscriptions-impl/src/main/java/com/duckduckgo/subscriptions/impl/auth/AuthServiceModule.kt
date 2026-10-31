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

package com.duckduckgo.subscriptions.impl.auth

import android.annotation.SuppressLint
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.Dns
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Named

@BindingContainer
@ContributesTo(AppScope::class)
object AuthServiceModule {
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    @Provides
    @SingleIn(AppScope::class)
    fun provideAuthService(
        @Named("nonCaching") okHttpClient: () -> OkHttpClient,
        @Named("nonCaching") retrofit: Retrofit,
        // This is to force the auth service calls to go outside the VPN
        // https://app.asana.com/1/137249556945/project/488551667048375/task/1212071195929229?focus=true
        dns: () -> Dns,
    ): AuthService {
        val okHttpClientWithoutRedirects = lazy {
            okHttpClient().newBuilder()
                .followRedirects(false)
                .dns(dns())
                .build()
        }

        return retrofit.newBuilder()
            .callFactory { okHttpClientWithoutRedirects.value.newCall(it) }
            .build()
            .create(AuthService::class.java)
    }
}
