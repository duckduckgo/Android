/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.store.impl

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeDatabase
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.duckchat.store.impl.store.FireModeDuckAiDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.io.File

/**
 * Provides the per-mode native Duck.ai storage. The regular ([DuckAiBridgeDatabase], `duck_ai_bridge.db`)
 * and Fire ([FireModeDuckAiDatabase], `fire_mode_duck_ai.db`) databases reuse the same entities and DAO types,
 * so each DAO and the chat-files directory are qualified with `@RegularMode` / `@FireMode`. Consumers either
 * inject a qualified [DuckAiBridgeStorage] directly or resolve one via `BrowserModeDataProvider<DuckAiBridgeStorage>`.
 */
@Module
@ContributesTo(AppScope::class)
object DuckAiBridgeModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideDatabase(context: Context): DuckAiBridgeDatabase =
        Room.databaseBuilder(context, DuckAiBridgeDatabase::class.java, "duck_ai_bridge.db")
            .addMigrations(DuckAiBridgeDatabase.MIGRATION_1_2, DuckAiBridgeDatabase.MIGRATION_2_3)
            .build()

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideFireDatabase(context: Context): FireModeDuckAiDatabase =
        Room.databaseBuilder(context, FireModeDuckAiDatabase::class.java, "fire_mode_duck_ai.db")
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    @RegularMode
    fun provideRegularChatsDao(db: DuckAiBridgeDatabase): DuckAiBridgeChatsDao = db.chatsDao()

    @Provides
    @FireMode
    fun provideFireChatsDao(db: FireModeDuckAiDatabase): DuckAiBridgeChatsDao = db.chatsDao()

    @Provides
    @RegularMode
    fun provideRegularSettingsDao(db: DuckAiBridgeDatabase): DuckAiBridgeSettingsDao = db.settingsDao()

    @Provides
    @FireMode
    fun provideFireSettingsDao(db: FireModeDuckAiDatabase): DuckAiBridgeSettingsDao = db.settingsDao()

    @Provides
    @RegularMode
    fun provideRegularFileMetaDao(db: DuckAiBridgeDatabase): DuckAiBridgeFileMetaDao = db.fileMetaDao()

    @Provides
    @FireMode
    fun provideFireFileMetaDao(db: FireModeDuckAiDatabase): DuckAiBridgeFileMetaDao = db.fileMetaDao()

    @Provides
    @SingleInstanceIn(AppScope::class)
    @DuckAiBridgeFilesDir
    fun provideFilesDir(context: Context): File =
        File(context.filesDir, "duck_ai_bridge_files")

    @Provides
    @FireDuckAiBridgeFilesDir
    fun provideFireFilesDir(context: Context): File =
        File(context.filesDir, "fire_mode_duck_ai_files")

    @Provides
    @SingleInstanceIn(AppScope::class)
    @RegularMode
    fun provideRegularStorage(
        @RegularMode settings: DuckAiBridgeSettingsDao,
        @RegularMode chats: DuckAiBridgeChatsDao,
        @RegularMode fileMeta: DuckAiBridgeFileMetaDao,
        @DuckAiBridgeFilesDir filesDir: Lazy<File>,
    ): DuckAiBridgeStorage = RealDuckAiBridgeStorage(settings, chats, fileMeta, filesDir)

    @Provides
    @SingleInstanceIn(AppScope::class)
    @FireMode
    fun provideFireStorage(
        @FireMode settings: DuckAiBridgeSettingsDao,
        @FireMode chats: DuckAiBridgeChatsDao,
        @FireMode fileMeta: DuckAiBridgeFileMetaDao,
        @FireDuckAiBridgeFilesDir filesDir: Lazy<File>,
    ): DuckAiBridgeStorage = RealDuckAiBridgeStorage(settings, chats, fileMeta, filesDir)
}
