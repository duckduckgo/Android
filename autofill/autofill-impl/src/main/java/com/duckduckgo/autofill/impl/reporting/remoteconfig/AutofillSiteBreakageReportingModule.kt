/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.reporting.remoteconfig

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.autofill.store.reporting.ALL_MIGRATIONS
import com.duckduckgo.autofill.store.reporting.AutofillSiteBreakageReportingDatabase
import com.duckduckgo.autofill.store.reporting.AutofillSiteBreakageReportingFeatureRepository
import com.duckduckgo.autofill.store.reporting.AutofillSiteBreakageReportingFeatureRepositoryImpl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
class AutofillSiteBreakageReportingModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun repository(
        database: AutofillSiteBreakageReportingDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): AutofillSiteBreakageReportingFeatureRepository {
        return AutofillSiteBreakageReportingFeatureRepositoryImpl(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun database(context: Context): AutofillSiteBreakageReportingDatabase {
        return Room.databaseBuilder(context, AutofillSiteBreakageReportingDatabase::class.java, "autofillSiteBreakageReporting.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    private val Context.autofillSiteBreakageReportingDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "autofill_site_breakage_reporting",
    )

    @Provides
    @SingleInstanceIn(AppScope::class)
    @AutofillSiteBreakageReporting
    fun provideImportPasswordsDesktopSyncDataStore(context: Context): DataStore<Preferences> {
        return context.autofillSiteBreakageReportingDataStore
    }
}

@Qualifier
annotation class AutofillSiteBreakageReporting
