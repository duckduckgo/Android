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
import androidx.room.Room
import androidx.room.RoomDatabase
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.httpauth.RealWebViewHttpAuthStore
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.httpauth.db.HttpAuthDatabase
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.db.MigrationsProvider
import com.duckduckgo.app.settings.db.SettingsDataStore
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(includes = [DaoModule::class])
class DatabaseModule {

    @Provides
    @Singleton
    fun provideWebViewHttpAuthStore(
        context: Context,
        fireproofWebsiteDao: FireproofWebsiteDao,
        dispatcherProvider: DispatcherProvider
    ): WebViewHttpAuthStore {
        val httpAuthDb = Room.databaseBuilder(context, HttpAuthDatabase::class.java, "http_auth.db")
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

        return RealWebViewHttpAuthStore(dispatcherProvider, fireproofWebsiteDao, httpAuthDb.httpAuthDao())
    }

    @Provides
    @Singleton
    fun provideDatabase(context: Context, migrationsProvider: MigrationsProvider): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
            .addMigrations(*migrationsProvider.ALL_MIGRATIONS.toTypedArray())
            .addCallback(migrationsProvider.BOOKMARKS_DB_ON_CREATE)
            .build()
    }

    @Provides
    fun provideDatabaseMigrations(
        context: Context,
        settingsDataStore: SettingsDataStore,
        addToHomeCapabilityDetector: AddToHomeCapabilityDetector
    ): MigrationsProvider {
        return MigrationsProvider(context, settingsDataStore, addToHomeCapabilityDetector)
    }
}
