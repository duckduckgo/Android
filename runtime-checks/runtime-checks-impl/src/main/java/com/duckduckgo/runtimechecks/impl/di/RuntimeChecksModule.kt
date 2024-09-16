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

package com.duckduckgo.runtimechecks.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.runtimechecks.store.ALL_MIGRATIONS
import com.duckduckgo.runtimechecks.store.RealRuntimeChecksRepository
import com.duckduckgo.runtimechecks.store.RuntimeChecksDatabase
import com.duckduckgo.runtimechecks.store.RuntimeChecksRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object RuntimeChecksModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideRuntimeChecksDatabase(context: Context): RuntimeChecksDatabase {
        return Room.databaseBuilder(context, RuntimeChecksDatabase::class.java, "runtime_checks.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideRuntimeChecksRepository(
        database: RuntimeChecksDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): RuntimeChecksRepository {
        return RealRuntimeChecksRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }
}
