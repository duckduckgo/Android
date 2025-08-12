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

package com.duckduckgo.pir.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.CaptchaResolver
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler
import com.duckduckgo.pir.impl.common.RealNativeBrokerActionHandler
import com.duckduckgo.pir.impl.common.actions.EventHandler
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngineFactory
import com.duckduckgo.pir.impl.common.actions.RealPirActionsRunnerStateEngineFactory
import com.duckduckgo.pir.impl.scripts.BrokerActionProcessor
import com.duckduckgo.pir.impl.scripts.PirMessagingInterface
import com.duckduckgo.pir.impl.scripts.RealBrokerActionProcessor
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.store.PirDatabase
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.RealPirDataStore
import com.duckduckgo.pir.impl.store.RealPirRepository
import com.duckduckgo.pir.impl.store.db.BrokerDao
import com.duckduckgo.pir.impl.store.db.BrokerJsonDao
import com.duckduckgo.pir.impl.store.db.JobSchedulingDao
import com.duckduckgo.pir.impl.store.db.OptOutResultsDao
import com.duckduckgo.pir.impl.store.db.ScanLogDao
import com.duckduckgo.pir.impl.store.db.ScanResultsDao
import com.duckduckgo.pir.impl.store.db.UserProfileDao
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
class PirModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun bindPirDatabase(context: Context): PirDatabase {
        return Room.databaseBuilder(context, PirDatabase::class.java, "pir.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideBrokerJsonDao(database: PirDatabase): BrokerJsonDao {
        return database.brokerJsonDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideBrokerDao(database: PirDatabase): BrokerDao {
        return database.brokerDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideScanResultsDao(database: PirDatabase): ScanResultsDao {
        return database.scanResultsDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideUserProfileDao(database: PirDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideScanLogDao(database: PirDatabase): ScanLogDao {
        return database.scanLogDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideOptOutResultsDao(database: PirDatabase): OptOutResultsDao {
        return database.optOutResultsDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideJobSchedulingDao(database: PirDatabase): JobSchedulingDao {
        return database.jobSchedulingDao()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providePirRepository(
        sharedPreferencesProvider: SharedPreferencesProvider,
        dispatcherProvider: DispatcherProvider,
        brokerJsonDao: BrokerJsonDao,
        brokerDao: BrokerDao,
        scanResultsDao: ScanResultsDao,
        currentTimeProvider: CurrentTimeProvider,
        moshi: Moshi,
        userProfileDao: UserProfileDao,
        scanLogDao: ScanLogDao,
        dbpService: DbpService,
        outResultsDao: OptOutResultsDao,
    ): PirRepository = RealPirRepository(
        moshi,
        dispatcherProvider,
        RealPirDataStore(sharedPreferencesProvider),
        currentTimeProvider,
        brokerJsonDao,
        brokerDao,
        scanResultsDao,
        userProfileDao,
        scanLogDao,
        dbpService,
        outResultsDao,
    )

    @Provides
    fun providesBrokerActionProcessor(
        pirMessagingInterface: PirMessagingInterface,
    ): BrokerActionProcessor {
        // Creates a new instance everytime is BrokerActionProcessor injected
        return RealBrokerActionProcessor(pirMessagingInterface)
    }

    @Provides
    fun provideNativeBrokerActionHandler(
        repository: PirRepository,
        dispatcherProvider: DispatcherProvider,
        captchaResolver: CaptchaResolver,
    ): NativeBrokerActionHandler {
        // Creates a new instance everytime is NativeBrokerActionHandler injected
        return RealNativeBrokerActionHandler(
            repository,
            dispatcherProvider,
            captchaResolver,
        )
    }

    @Provides
    fun providePirActionsRunnerStateEngineFactory(
        eventHandlers: PluginPoint<EventHandler>,
        dispatcherProvider: DispatcherProvider,
        @AppCoroutineScope coroutineScope: CoroutineScope,
    ): PirActionsRunnerStateEngineFactory {
        return RealPirActionsRunnerStateEngineFactory(
            eventHandlers,
            dispatcherProvider,
            coroutineScope,
        )
    }
}
