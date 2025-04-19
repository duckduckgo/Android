/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.features.messagebridge.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.ALL_MIGRATIONS
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeDatabase
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.MessageBridgeRepository
import com.duckduckgo.contentscopescripts.impl.features.messagebridge.store.RealMessageBridgeRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object MessageBridgeModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideMessageBridgeDatabase(context: Context): MessageBridgeDatabase {
        return Room.databaseBuilder(context, MessageBridgeDatabase::class.java, "message_bridge.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideMessageBridgeRepository(
        database: MessageBridgeDatabase,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): MessageBridgeRepository {
        return RealMessageBridgeRepository(database, coroutineScope, dispatcherProvider)
    }
}
