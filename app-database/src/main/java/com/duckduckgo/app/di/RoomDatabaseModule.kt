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
import com.duckduckgo.app.bookmarks.migration.AppDatabaseBookmarksMigrationCallbackProvider
import com.duckduckgo.app.fire.db.FireModeDatabase
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.db.LegacyFireproofSettingsMigration
import com.duckduckgo.app.global.db.MigrationsProvider
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
object RoomDatabaseModule {

    @Provides
    fun provideAppDatabaseBookmarksMigrationCallbackProvider(
        appDatabase: Lazy<AppDatabase>,
        appBuildConfig: AppBuildConfig,
    ): AppDatabaseBookmarksMigrationCallbackProvider {
        return AppDatabaseBookmarksMigrationCallbackProvider(appDatabase, appBuildConfig)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAppDatabase(
        context: Context,
        migrationsProvider: MigrationsProvider,
        databaseBookmarksMigrationCallbackProvider: AppDatabaseBookmarksMigrationCallbackProvider,
    ): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
            .addMigrations(*migrationsProvider.ALL_MIGRATIONS.toTypedArray())
            .addCallback(migrationsProvider.BOOKMARKS_DB_ON_CREATE)
            .addCallback(databaseBookmarksMigrationCallbackProvider.provideCallbacks())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .enableMultiInstanceInvalidation()
            .build()
    }

    @Provides
    fun provideDatabaseMigrations(
        context: Context,
        legacyFireproofSettingsMigration: LegacyFireproofSettingsMigration,
    ): MigrationsProvider {
        return MigrationsProvider(context, legacyFireproofSettingsMigration)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideFireModeDatabase(context: Context): FireModeDatabase {
        return Room.databaseBuilder(context, FireModeDatabase::class.java, "fire_mode.db")
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .fallbackToDestructiveMigration(true)
            .build()
    }
}
