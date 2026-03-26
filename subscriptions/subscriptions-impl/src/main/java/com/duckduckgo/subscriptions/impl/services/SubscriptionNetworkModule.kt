/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.services

import android.annotation.SuppressLint
import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import javax.inject.Named
import javax.inject.Qualifier

@Module
@ContributesTo(AppScope::class)
class SubscriptionNetworkModule {

    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    private annotation class SubscriptionCachedClient

    @Provides
    @SubscriptionCachedClient
    @SingleInstanceIn(AppScope::class)
    fun provideSubscriptionsCustomCacheHttpClient(
        context: Context,
        @Named("api") okHttpClient: OkHttpClient,
    ): OkHttpClient {
        val cacheLocation = File(context.cacheDir, SUBSCRIPTION_CACHE_FILE)
        val cacheSize: Long = 128 * 1024 // 128KB, responses are 1kb so this is more than enough
        val cache = Cache(cacheLocation, cacheSize)
        return okHttpClient.newBuilder()
            .cache(cache)
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun providesSubscriptionsCachedService(
        @Named(value = "api") retrofit: Retrofit,
        @SubscriptionCachedClient customClient: Lazy<OkHttpClient>,
    ): SubscriptionsCachedService {
        val customRetrofit = retrofit.newBuilder()
            .callFactory { customClient.get().newCall(it) }
            .build()

        return customRetrofit.create(SubscriptionsCachedService::class.java)
    }

    companion object {
        const val SUBSCRIPTION_CACHE_FILE = "subscriptionsCache"
    }
}
