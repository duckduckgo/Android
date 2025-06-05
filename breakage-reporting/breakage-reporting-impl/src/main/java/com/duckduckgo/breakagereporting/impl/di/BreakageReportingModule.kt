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

package com.duckduckgo.breakagereporting.impl.di

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.breakagereporting.impl.ALL_MIGRATIONS
import com.duckduckgo.breakagereporting.impl.BreakageReportingDatabase
import com.duckduckgo.breakagereporting.impl.BreakageReportingRepository
import com.duckduckgo.breakagereporting.impl.RealBreakageReportingRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object BreakageReportingModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideBreakageReportingDatabase(databaseProvider: DatabaseProvider): BreakageReportingDatabase {
        return databaseProvider.buildRoomDatabase(
            BreakageReportingDatabase::class.java,
            "breakage_reporting.db",
            config = RoomDatabaseConfig(
                fallbackToDestructiveMigration = true,
                enableMultiInstanceInvalidation = true,
                migrations = ALL_MIGRATIONS,
            ),
        )
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideBreakageReportingRepository(
        database: BreakageReportingDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): BreakageReportingRepository {
        return RealBreakageReportingRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }
}
