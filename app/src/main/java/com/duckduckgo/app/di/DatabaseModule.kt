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
import android.webkit.WebViewDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.db.MigrationsProvider
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(includes = [DaoModule::class])
class DatabaseModule {

    @Provides
    @Singleton
    fun provideWebviewDatabase(context: Context): WebViewDatabase {
        return WebViewDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDatabase(context: Context, migrationsProvider: MigrationsProvider): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
            .addMigrations(*migrationsProvider.ALL_MIGRATIONS.toTypedArray())
            .addCallback(migrationsProvider.BOOKMARKS_DB_ON_CREATE)
            .addCallback(migrationsProvider.CHANGE_JOURNAL_ON_OPEN)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
    }

    @Provides
    fun provideDatabaseMigrations(context: Context): MigrationsProvider {
        return MigrationsProvider(context)
    }
}
