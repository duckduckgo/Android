/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.store.ALL_MIGRATIONS
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesDataStore
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesSharedPreferences
import com.duckduckgo.privacy.config.store.RealPrivacyConfigRepository
import com.duckduckgo.privacy.config.store.RealPrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.amplinks.AmpLinksRepository
import com.duckduckgo.privacy.config.store.features.amplinks.RealAmpLinksRepository
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingRepository
import com.duckduckgo.privacy.config.store.features.contentblocking.RealContentBlockingRepository
import com.duckduckgo.privacy.config.store.features.drm.DrmRepository
import com.duckduckgo.privacy.config.store.features.drm.RealDrmRepository
import com.duckduckgo.privacy.config.store.features.gpc.GpcDataStore
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.duckduckgo.privacy.config.store.features.gpc.GpcSharedPreferences
import com.duckduckgo.privacy.config.store.features.gpc.RealGpcRepository
import com.duckduckgo.privacy.config.store.features.https.HttpsRepository
import com.duckduckgo.privacy.config.store.features.https.RealHttpsRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.RealTrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.trackingparameters.RealTrackingParametersRepository
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.RealUnprotectedTemporaryRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object DatabaseModule {

    @Provides
    @ConfigPersisterPreferences
    fun providePrivacyConfigPersisterPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences("com.duckduckgo.privacy.config.persister.preferences.v1", Context.MODE_PRIVATE)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providePrivacyConfigDatabase(context: Context): PrivacyConfigDatabase {
        return Room.databaseBuilder(context, PrivacyConfigDatabase::class.java, "privacy_config.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providePrivacyConfigRepository(database: PrivacyConfigDatabase): PrivacyConfigRepository {
        return RealPrivacyConfigRepository(database)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providePTrackerAllowlistRepository(
        database: PrivacyConfigDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): TrackerAllowlistRepository {
        return RealTrackerAllowlistRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideContentBlockingRepository(
        database: PrivacyConfigDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): ContentBlockingRepository {
        return RealContentBlockingRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideGpcDataStore(context: Context): GpcDataStore {
        return GpcSharedPreferences(context)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providePrivacyFeatureTogglesDataStore(context: Context): PrivacyFeatureTogglesDataStore {
        return PrivacyFeatureTogglesSharedPreferences(context)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideGpcRepository(
        gpcDataStore: GpcDataStore,
        database: PrivacyConfigDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): GpcRepository {
        return RealGpcRepository(gpcDataStore, database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideHttpsRepository(
        database: PrivacyConfigDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): HttpsRepository {
        return RealHttpsRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideUnprotectedTemporaryRepository(
        database: PrivacyConfigDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): UnprotectedTemporaryRepository {
        return RealUnprotectedTemporaryRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providePrivacyFeatureTogglesRepository(privacyFeatureTogglesDataStore: PrivacyFeatureTogglesDataStore): PrivacyFeatureTogglesRepository {
        return RealPrivacyFeatureTogglesRepository(privacyFeatureTogglesDataStore)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideDrmRepository(
        database: PrivacyConfigDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): DrmRepository {
        return RealDrmRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAmpLinksRepository(
        database: PrivacyConfigDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): AmpLinksRepository {
        return RealAmpLinksRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideTrackingParametersRepository(
        database: PrivacyConfigDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): TrackingParametersRepository {
        return RealTrackingParametersRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }
}
