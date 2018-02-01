/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.di

import android.content.Context
import com.duckduckgo.app.surrogates.api.ResourceSurrogateListService
import com.duckduckgo.app.autocomplete.api.AutoCompleteService
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeListService
import com.duckduckgo.app.trackerdetection.api.TrackerListService
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton


@Module
class NetworkModule {

    @Provides
    @Singleton
    fun okHttpClient(context: Context): OkHttpClient {
        val cache = Cache(context.cacheDir, CACHE_SIZE)

        return OkHttpClient.Builder()
                .cache(cache)
                .build()
    }

    @Provides
    @Singleton
    fun retrofit(okHttpClient: OkHttpClient, moshi: Moshi, context: Context): Retrofit {
        return Retrofit.Builder()
                .baseUrl(context.getString(R.string.baseUrl))
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
    }

    @Provides
    fun trackerListService(retrofit: Retrofit): TrackerListService =
            retrofit.create(TrackerListService::class.java)

    @Provides
    fun httpsUpgradeListService(retrofit: Retrofit): HttpsUpgradeListService =
            retrofit.create(HttpsUpgradeListService::class.java)

    @Provides
    fun autoCompleteService(retrofit: Retrofit): AutoCompleteService =
            retrofit.create(AutoCompleteService::class.java)

    @Provides
    fun surrogatesService(retrofit: Retrofit): ResourceSurrogateListService =
        retrofit.create(ResourceSurrogateListService::class.java)

    companion object {
        private const val CACHE_SIZE: Long = 10 * 1024 * 1024 // 10MB
    }
}