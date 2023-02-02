/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.windows.impl.di

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.windows.impl.waitlist.api.Url.API
import com.duckduckgo.windows.impl.waitlist.api.WindowsWaitlistService
import com.duckduckgo.windows.store.RealWindowsWaitlistRepository
import com.duckduckgo.windows.store.WindowsWaitlistDataStore
import com.duckduckgo.windows.store.WindowsWaitlistDataStoreSharedPreferences
import com.duckduckgo.windows.store.WindowsWaitlistRepository
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import javax.inject.Named
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@ContributesTo(AppScope::class)
class WindowsModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun apiRetrofit(@Named("api") okHttpClient: OkHttpClient): WindowsWaitlistService {
        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(API)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(WindowsWaitlistService::class.java)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideRepository(dataStore: WindowsWaitlistDataStore): WindowsWaitlistRepository {
        return RealWindowsWaitlistRepository(dataStore)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideDataStore(context: Context): WindowsWaitlistDataStore {
        return WindowsWaitlistDataStoreSharedPreferences(context)
    }
}
