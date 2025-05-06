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

package com.duckduckgo.brokensite.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.brokensite.impl.BrokenSitePromptDataStore
import com.duckduckgo.brokensite.impl.BrokenSiteRefreshesInMemoryStore
import com.duckduckgo.brokensite.impl.BrokenSiteReportRepository
import com.duckduckgo.brokensite.impl.RealBrokenSiteReportRepository
import com.duckduckgo.brokensite.store.ALL_MIGRATIONS
import com.duckduckgo.brokensite.store.BrokenSiteDatabase
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
class BrokenSiteModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideBrokenSiteReportRepository(
        database: BrokenSiteDatabase,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        brokenSitePromptDataStore: BrokenSitePromptDataStore,
        brokenSiteRefreshesInMemoryStore: BrokenSiteRefreshesInMemoryStore,
    ): BrokenSiteReportRepository {
        return RealBrokenSiteReportRepository(
            database,
            coroutineScope,
            dispatcherProvider,
            brokenSitePromptDataStore,
            brokenSiteRefreshesInMemoryStore,
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideBrokenSiteDatabase(context: Context): BrokenSiteDatabase {
        return Room.databaseBuilder(context, BrokenSiteDatabase::class.java, "broken_site.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }
}
