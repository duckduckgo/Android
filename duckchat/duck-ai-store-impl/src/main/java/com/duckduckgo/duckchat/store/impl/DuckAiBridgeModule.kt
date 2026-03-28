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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeDatabase
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.io.File

@Module
@ContributesTo(AppScope::class)
object DuckAiBridgeModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideDatabase(context: Context): DuckAiBridgeDatabase =
        Room.databaseBuilder(context, DuckAiBridgeDatabase::class.java, "duck_ai_bridge.db")
            .build()

    @Provides
    fun provideChatsDao(db: DuckAiBridgeDatabase): DuckAiBridgeChatsDao = db.chatsDao()

    @Provides
    fun provideSettingsDao(db: DuckAiBridgeDatabase): DuckAiBridgeSettingsDao = db.settingsDao()

    @Provides
    @DuckAiBridgeFilesDir
    fun provideFilesDir(context: Context): File =
        File(context.filesDir, "duck_ai_bridge_files")
}
