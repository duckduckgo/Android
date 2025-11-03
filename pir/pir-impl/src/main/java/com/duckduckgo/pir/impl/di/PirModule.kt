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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.CaptchaResolver
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler
import com.duckduckgo.pir.impl.common.RealNativeBrokerActionHandler
import com.duckduckgo.pir.impl.common.actions.EventHandler
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngineFactory
import com.duckduckgo.pir.impl.common.actions.RealPirActionsRunnerStateEngineFactory
import com.duckduckgo.pir.impl.scripts.BrokerActionProcessor
import com.duckduckgo.pir.impl.scripts.PirMessagingInterface
import com.duckduckgo.pir.impl.scripts.RealBrokerActionProcessor
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.SolveCaptcha
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ClickResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ConditionResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExpectationResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.FillFormResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.SolveCaptchaResponse
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.store.PirDatabase
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.RealPirDataStore
import com.duckduckgo.pir.impl.store.RealPirRepository
import com.duckduckgo.pir.impl.store.db.BrokerDao
import com.duckduckgo.pir.impl.store.db.BrokerJsonDao
import com.duckduckgo.pir.impl.store.db.EmailConfirmationLogDao
import com.duckduckgo.pir.impl.store.db.ExtractedProfileDao
import com.duckduckgo.pir.impl.store.db.JobSchedulingDao
import com.duckduckgo.pir.impl.store.db.OptOutResultsDao
import com.duckduckgo.pir.impl.store.db.ScanLogDao
import com.duckduckgo.pir.impl.store.db.ScanResultsDao
import com.duckduckgo.pir.impl.store.db.UserProfileDao
import com.duckduckgo.pir.impl.store.secure.PirSecureStorageDatabaseFactory
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import javax.inject.Named

@Module
@ContributesTo(AppScope::class)
class PirModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun bindPirDatabase(
        databaseFactory: PirSecureStorageDatabaseFactory,
    ): PirDatabase {
        return runBlocking {
            databaseFactory.getDatabase()
        } ?: throw IllegalStateException("Failed to create PIR encrypted database")
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

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideExtractedProfileDao(database: PirDatabase): ExtractedProfileDao {
        return database.extractedProfileDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEmailConfirmationLogDao(database: PirDatabase): EmailConfirmationLogDao {
        return database.emailConfirmationLogDao()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providePirRepository(
        sharedPreferencesProvider: SharedPreferencesProvider,
        dispatcherProvider: DispatcherProvider,
        brokerJsonDao: BrokerJsonDao,
        brokerDao: BrokerDao,
        currentTimeProvider: CurrentTimeProvider,
        userProfileDao: UserProfileDao,
        dbpService: DbpService,
        extractedProfileDao: ExtractedProfileDao,
    ): PirRepository = RealPirRepository(
        dispatcherProvider,
        RealPirDataStore(sharedPreferencesProvider),
        currentTimeProvider,
        brokerJsonDao,
        brokerDao,
        userProfileDao,
        dbpService,
        extractedProfileDao,
    )

    @Provides
    fun providesBrokerActionProcessor(
        pirMessagingInterface: PirMessagingInterface,
        @Named("pir") moshi: Moshi,
    ): BrokerActionProcessor {
        // Creates a new instance everytime is BrokerActionProcessor injected
        return RealBrokerActionProcessor(pirMessagingInterface, moshi)
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

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("pir")
    fun providePirMoshi(moshi: Moshi): Moshi {
        return moshi.newBuilder()
            .add(
                PolymorphicJsonAdapterFactory.of(PirScriptRequestData::class.java, "data")
                    .withSubtype(SolveCaptcha::class.java, "solveCaptcha")
                    .withSubtype(UserProfile::class.java, "userProfile"),
            ).add(
                PolymorphicJsonAdapterFactory.of(BrokerAction::class.java, "actionType")
                    .withSubtype(BrokerAction.Extract::class.java, "extract")
                    .withSubtype(BrokerAction.Expectation::class.java, "expectation")
                    .withSubtype(BrokerAction.Click::class.java, "click")
                    .withSubtype(BrokerAction.FillForm::class.java, "fillForm")
                    .withSubtype(BrokerAction.Navigate::class.java, "navigate")
                    .withSubtype(BrokerAction.GetCaptchaInfo::class.java, "getCaptchaInfo")
                    .withSubtype(BrokerAction.SolveCaptcha::class.java, "solveCaptcha")
                    .withSubtype(BrokerAction.EmailConfirmation::class.java, "emailConfirmation")
                    .withSubtype(BrokerAction.Condition::class.java, "condition"),
            ).add(
                PolymorphicJsonAdapterFactory.of(BrokerStep::class.java, "stepType")
                    .withSubtype(ScanStep::class.java, "scan")
                    .withSubtype(OptOutStep::class.java, "optOut"),
            ).add(
                PolymorphicJsonAdapterFactory.of(PirSuccessResponse::class.java, "actionType")
                    .withSubtype(NavigateResponse::class.java, "navigate")
                    .withSubtype(ExtractedResponse::class.java, "extract")
                    .withSubtype(GetCaptchaInfoResponse::class.java, "getCaptchaInfo")
                    .withSubtype(SolveCaptchaResponse::class.java, "solveCaptcha")
                    .withSubtype(ClickResponse::class.java, "click")
                    .withSubtype(ExpectationResponse::class.java, "expectation")
                    .withSubtype(FillFormResponse::class.java, "fillForm")
                    .withSubtype(ConditionResponse::class.java, "condition"),
            )
            .add(KotlinJsonAdapterFactory())
            .build()
    }
}
