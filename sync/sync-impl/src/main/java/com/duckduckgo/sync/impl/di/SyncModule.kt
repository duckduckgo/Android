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

package com.duckduckgo.sync.impl.di

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.store.EncryptedSharedPrefsProvider
import com.duckduckgo.sync.store.SharedPrefsProvider
import com.duckduckgo.sync.store.SyncSharedPrefsStore
import com.duckduckgo.sync.store.SyncStore
import com.duckduckgo.sync.lib.NativeLib
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
object SyncStoreModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSyncStore(sharedPrefsProvider: SharedPrefsProvider): SyncStore {
        return SyncSharedPrefsStore(sharedPrefsProvider)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideSharedPrefsProvider(context: Context): SharedPrefsProvider {
        return EncryptedSharedPrefsProvider(context)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesNativeLib(context: Context): NativeLib {
        return NativeLib(context)
    }
}
