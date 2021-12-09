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
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.duckduckgo.privacy.config.impl.network.PrivacyConfigService
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePluginPoint
import com.duckduckgo.privacy.config.store.ALL_MIGRATIONS
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesDataStore
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesSharedPreferences
import com.duckduckgo.privacy.config.store.RealPrivacyConfigRepository
import com.duckduckgo.privacy.config.store.RealPrivacyFeatureTogglesRepository
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
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.RealUnprotectedTemporaryRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
abstract class PrivacyFeaturesBindingModule {

    @Multibinds
    abstract fun providePrivacyFeatureStorePlugins(): Set<@JvmSuppressWildcards PrivacyFeaturePlugin>

}

@Module
@ContributesTo(AppObjectGraph::class)
class NetworkModule {

    @Provides
    @Singleton
    fun apiRetrofit(@Named("api") okHttpClient: OkHttpClient): PrivacyConfigService {
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(AppUrl.Url.API)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(PrivacyConfigService::class.java)
    }

    @Provides
    @Singleton
    fun providePrivacyFeaturePluginPoint(customConfigs: Set<@JvmSuppressWildcards PrivacyFeaturePlugin>): PluginPoint<PrivacyFeaturePlugin> {
        return PrivacyFeaturePluginPoint(customConfigs)
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
class DatabaseModule {

    @Singleton
    @Provides
    fun providePrivacyConfigDatabase(context: Context): PrivacyConfigDatabase {
        return Room.databaseBuilder(context, PrivacyConfigDatabase::class.java, "privacy_config.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @Singleton
    @Provides
    fun providePrivacyConfigRepository(database: PrivacyConfigDatabase): PrivacyConfigRepository {
        return RealPrivacyConfigRepository(database)
    }

    @Singleton
    @Provides
    fun providePTrackerAllowlistRepository(database: PrivacyConfigDatabase, @AppCoroutineScope coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider): TrackerAllowlistRepository {
        return RealTrackerAllowlistRepository(database, coroutineScope, dispatcherProvider)
    }

    @Singleton
    @Provides
    fun provideContentBlockingRepository(database: PrivacyConfigDatabase, @AppCoroutineScope coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider): ContentBlockingRepository {
        return RealContentBlockingRepository(database, coroutineScope, dispatcherProvider)
    }

    @Singleton
    @Provides
    fun provideGpcDataStore(context: Context): GpcDataStore {
        return GpcSharedPreferences(context)
    }

    @Singleton
    @Provides
    fun providePrivacyFeatureTogglesDataStore(context: Context): PrivacyFeatureTogglesDataStore {
        return PrivacyFeatureTogglesSharedPreferences(context)
    }

    @Singleton
    @Provides
    fun provideGpcRepository(gpcDataStore: GpcDataStore, database: PrivacyConfigDatabase, @AppCoroutineScope coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider): GpcRepository {
        return RealGpcRepository(gpcDataStore, database, coroutineScope, dispatcherProvider)
    }

    @Singleton
    @Provides
    fun provideHttpsRepository(database: PrivacyConfigDatabase, @AppCoroutineScope coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider): HttpsRepository {
        return RealHttpsRepository(database, coroutineScope, dispatcherProvider)
    }

    @Singleton
    @Provides
    fun provideUnprotectedTemporaryRepository(database: PrivacyConfigDatabase, @AppCoroutineScope coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider): UnprotectedTemporaryRepository {
        return RealUnprotectedTemporaryRepository(database, coroutineScope, dispatcherProvider)
    }

    @Singleton
    @Provides
    fun providePrivacyFeatureTogglesRepository(privacyFeatureTogglesDataStore: PrivacyFeatureTogglesDataStore): PrivacyFeatureTogglesRepository {
        return RealPrivacyFeatureTogglesRepository(privacyFeatureTogglesDataStore)
    }

    @Singleton
    @Provides
    fun provideDrmRepository(database: PrivacyConfigDatabase, @AppCoroutineScope coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider): DrmRepository {
        return RealDrmRepository(database, coroutineScope, dispatcherProvider)
    }
}
