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

package com.duckduckgo.app.remotemessage.impl

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.remotemessage.store.ALL_MIGRATIONS
import com.duckduckgo.app.remotemessage.store.LocalRemoteMessagingConfigRepository
import com.duckduckgo.app.remotemessage.store.RemoteMessagingConfigRepository
import com.duckduckgo.app.remotemessage.store.RemoteMessagingDatabase
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
abstract class RemoteMessagingModuleBindingModule {

    @Multibinds
    abstract fun provideMatchingAttributesPlugins(): Set<@JvmSuppressWildcards MatchingAttributePlugin>
}

@Module
@ContributesTo(AppObjectGraph::class)
class DomainModule {
    @Provides
    @Singleton
    fun providesRemoteMessagingConfigDownloader(
        remoteConfig: RemoteMessagingService,
        remoteMessagingConfigProcessor: RemoteMessagingConfigProcessor
    ): RemoteMessagingConfigDownloader {
        return RealRemoteMessagingConfigDownloader(remoteConfig, remoteMessagingConfigProcessor)
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
class NetworkModule {

    @Provides
    @Singleton
    fun apiRetrofit(@Named("api") okHttpClient: OkHttpClient): RemoteMessagingService {
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(AppUrl.Url.API)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(RemoteMessagingService::class.java)
    }

    @Provides
    @Singleton
    fun providesMatchingAttributePluginPoint(customConfigs: Set<@JvmSuppressWildcards MatchingAttributePlugin>): PluginPoint<MatchingAttributePlugin> {
        return MatchingAttributePluginPoint(customConfigs)
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
class DataSourceModule {
    @Singleton
    @Provides
    fun providesRemoteMessagingConfigProcessor(
        matchingAttributePluginPoint: PluginPoint<MatchingAttributePlugin>,
        remoteMessagingConfigRepository: RemoteMessagingConfigRepository
    ): RemoteMessagingConfigProcessor {
        return RealRemoteMessagingConfigProcessor(
            matchingAttributePluginPoint,
            remoteMessagingConfigRepository
        )
    }

    @Singleton
    @Provides
    fun providesRemoteMessagingConfigRepository(database: RemoteMessagingDatabase): RemoteMessagingConfigRepository {
        return LocalRemoteMessagingConfigRepository(database)
    }

    @Singleton
    @Provides
    fun providesRemoteMessagingDatabase(context: Context): RemoteMessagingDatabase {
        return Room.databaseBuilder(context, RemoteMessagingDatabase::class.java, "remote_messaging.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }
}
