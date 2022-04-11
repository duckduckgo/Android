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

package com.duckduckgo.macos_impl.di

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.macos_impl.waitlist.api.MacOsWaitlistService
import com.duckduckgo.macos_impl.waitlist.api.Url.API
import com.duckduckgo.macos_store.MacOsWaitlistDataStore
import com.duckduckgo.macos_store.MacOsWaitlistDataStoreSharedPreferences
import com.duckduckgo.macos_store.MacOsWaitlistRepository
import com.duckduckgo.macos_store.RealMacOsWaitlistRepository
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named

@Module
@ContributesTo(AppScope::class)
object NetworkModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun apiRetrofit(@Named("api") okHttpClient: OkHttpClient): MacOsWaitlistService {
        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(API)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(MacOsWaitlistService::class.java)
    }
}

@Module
@ContributesTo(AppScope::class)
object DatabaseModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providePrivacyConfigRepository(dataStore: MacOsWaitlistDataStore): MacOsWaitlistRepository {
        return RealMacOsWaitlistRepository(dataStore)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideMacOsWaitlistDataStore(context: Context): MacOsWaitlistDataStore {
        return MacOsWaitlistDataStoreSharedPreferences(context)
    }
}
