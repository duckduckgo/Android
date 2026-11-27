/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.statistics.api

import android.annotation.SuppressLint
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Qualifier

/**
 * Pixel get their own OkHttp Dispatcher so its per-host request limit can be lower than the shared client's without affecting other hosts.
 * A lower limit means a burst of pixels opens fewer new TCP connections at once.
 * The client is derived with newBuilder(), so it shares the connection pool, interceptors and timeouts.
 */
@BindingContainer
@ContributesTo(AppScope::class)
class PixelNetworkModule {

    @Provides
    @SingleIn(AppScope::class)
    @PixelHttpClient
    fun providePixelHttpClient(
        @Named("nonCaching") okHttpClient: OkHttpClient,
        requestLimitUpdater: PixelRequestLimitUpdater,
    ): OkHttpClient {
        return okHttpClient.newBuilder()
            .dispatcher(requestLimitUpdater.dispatcher)
            .build()
    }

    @Provides
    @SingleIn(AppScope::class)
    // @ContributesNonCachingServiceApi can't use a custom OkHttpClient, and pixels need their own Dispatcher
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun providePixelService(
        @Named("nonCaching") retrofit: Retrofit,
        @PixelHttpClient pixelHttpClient: Lazy<OkHttpClient>,
    ): PixelService {
        return retrofit.newBuilder()
            .callFactory { pixelHttpClient.value.newCall(it) }
            .build()
            .create(PixelService::class.java)
    }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class PixelHttpClient
