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

package com.duckduckgo.duckchat.localserver.impl

import android.content.Context
import androidx.room.Room
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiLocalServerDatabase
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiChatsDao
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiLocalServerDatabase.Companion.MIGRATION_1_2
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiLocalServerDatabase.Companion.MIGRATION_2_3
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiMigrationDao
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiSettingsDao
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.io.File

@Module
@ContributesTo(AppScope::class)
object DuckAiLocalServerModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideDatabase(context: Context): DuckAiLocalServerDatabase =
        Room.databaseBuilder(context, DuckAiLocalServerDatabase::class.java, "duck_ai_local_server.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideSettingsDao(db: DuckAiLocalServerDatabase): DuckAiSettingsDao = db.settingsDao()

    @Provides
    fun provideMigrationDao(db: DuckAiLocalServerDatabase): DuckAiMigrationDao = db.migrationDao()

    @Provides
    fun provideChatsDao(db: DuckAiLocalServerDatabase): DuckAiChatsDao = db.chatsDao()

    @Provides
    @DuckAiImagesDir
    fun provideImagesDir(context: Context): File =
        File(context.filesDir, "duck_ai_images")
}
