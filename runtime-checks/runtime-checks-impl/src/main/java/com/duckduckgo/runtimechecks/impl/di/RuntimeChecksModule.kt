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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.runtimechecks.store.ALL_MIGRATIONS
import com.duckduckgo.runtimechecks.store.RealRuntimeChecksRepository
import com.duckduckgo.runtimechecks.store.RuntimeChecksDatabase
import com.duckduckgo.runtimechecks.store.RuntimeChecksRepository
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@BindingContainer
@ContributesTo(AppScope::class)
object RuntimeChecksModule {

    @SingleIn(AppScope::class)
    @Provides
    fun provideRuntimeChecksDatabase(databaseProvider: DatabaseProvider): RuntimeChecksDatabase {
        return databaseProvider.buildRoomDatabase(
            RuntimeChecksDatabase::class.java,
            "runtime_checks.db",
            config = RoomDatabaseConfig(
                enableMultiInstanceInvalidation = true,
                fallbackToDestructiveMigration = true,
                migrations = ALL_MIGRATIONS,
            ),
        )
    }

    @SingleIn(AppScope::class)
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
