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

package com.duckduckgo.elementhiding.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.elementhiding.store.ALL_MIGRATIONS
import com.duckduckgo.elementhiding.store.ElementHidingDatabase
import com.duckduckgo.elementhiding.store.ElementHidingRepository
import com.duckduckgo.elementhiding.store.RealElementHidingRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object ElementHidingModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideElementHidingDatabase(context: Context): ElementHidingDatabase {
        return Room.databaseBuilder(context, ElementHidingDatabase::class.java, "element_hiding.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideElementHidingRepository(
        database: ElementHidingDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): ElementHidingRepository {
        return RealElementHidingRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }
}
