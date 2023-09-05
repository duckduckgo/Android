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
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.crypto.SyncNativeLib
import com.duckduckgo.sync.impl.AppQREncoder
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.engine.AppSyncStateRepository
import com.duckduckgo.sync.impl.engine.SyncStateRepository
import com.duckduckgo.sync.impl.stats.RealSyncStatsRepository
import com.duckduckgo.sync.impl.stats.SyncStatsRepository
import com.duckduckgo.sync.store.EncryptedSharedPrefsProvider
import com.duckduckgo.sync.store.SharedPrefsProvider
import com.duckduckgo.sync.store.SyncDatabase
import com.duckduckgo.sync.store.SyncSharedPrefsStore
import com.duckduckgo.sync.store.SyncStore
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object SyncStoreModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSyncStore(
        sharedPrefsProvider: SharedPrefsProvider,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
    ): SyncStore {
        return SyncSharedPrefsStore(sharedPrefsProvider, appCoroutineScope)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideSharedPrefsProvider(context: Context): SharedPrefsProvider {
        return EncryptedSharedPrefsProvider(context)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesNativeLib(context: Context): SyncLib {
        return SyncNativeLib(context)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideQREncoder(
        context: Context,
    ): QREncoder {
        // create instance of BarcodeEncoder here so we don't need to add third-party dependency to :app module (classpath)
        return AppQREncoder(context, BarcodeEncoder())
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideSyncDatabase(context: Context): SyncDatabase {
        return Room.databaseBuilder(context, SyncDatabase::class.java, "sync.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSyncStateRepository(syncDatabase: SyncDatabase): SyncStateRepository {
        return AppSyncStateRepository(syncDatabase.syncAttemptsDao())
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideSyncStatsRepository(
        syncStateRepository: SyncStateRepository,
    ): SyncStatsRepository {
        return RealSyncStatsRepository(syncStateRepository)
    }
}
