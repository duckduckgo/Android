/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.clicktoload.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.clicktoload.store.ALL_MIGRATIONS
import com.duckduckgo.clicktoload.store.ClickToLoadDatabase
import com.duckduckgo.clicktoload.store.ClickToLoadRepository
import com.duckduckgo.clicktoload.store.RealClickToLoadRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object ClickToLoadModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideClickToLoadDatabase(context: Context): ClickToLoadDatabase {
        return Room.databaseBuilder(context, ClickToLoadDatabase::class.java, "click_to_load.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideClickToLoadRepository(
        database: ClickToLoadDatabase,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): ClickToLoadRepository {
        return RealClickToLoadRepository(database, coroutineScope, dispatcherProvider)
    }
}
