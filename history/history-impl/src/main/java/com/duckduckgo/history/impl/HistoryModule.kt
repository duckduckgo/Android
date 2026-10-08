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

package com.duckduckgo.history.impl

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.history.impl.store.ALL_MIGRATIONS
import com.duckduckgo.history.impl.store.HistoryDataStore
import com.duckduckgo.history.impl.store.HistoryDatabase
import com.duckduckgo.history.impl.store.SharedPreferencesHistoryDataStore
import dagger.Module
import dagger.Provides
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

@Module
@ContributesTo(AppScope::class)
class HistoryModule {

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryRepository(
        historyDatabase: HistoryDatabase,
        dispatcherProvider: DispatcherProvider,
        historyDataStore: HistoryDataStore,
    ): HistoryRepository {
        return RealHistoryRepository(
            historyDatabase.historyDao(),
            dispatcherProvider,
            historyDataStore,
        )
    }

    @SingleIn(AppScope::class)
    @Provides
    fun provideDatabase(context: Context): HistoryDatabase {
        return Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db")
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleIn(AppScope::class)
    @Provides
    fun provideHistoryDataStore(context: Context): HistoryDataStore {
        return SharedPreferencesHistoryDataStore(context)
    }
}
