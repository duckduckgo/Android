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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.Multibinds
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
abstract class RemoteMessagingModuleBindingModule {

    @Multibinds
    abstract fun provideMatchingAttributesPlugins(): Set<@JvmSuppressWildcards MatchingAttributePlugin>

    @Multibinds
    abstract fun provideMessagePlugins(): Set<@JvmSuppressWildcards MessagePlugin>
}

@Module
@ContributesTo(AppScope::class)
class DomainModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigDownloader(
        remoteConfig: RemoteMessagingService,
        remoteMessagingConfigProcessor: RemoteMessagingConfigProcessor
    ): RemoteMessagingConfigDownloader {
        return RealRemoteMessagingConfigDownloader(remoteConfig, remoteMessagingConfigProcessor)
    }
}

@Module
@ContributesTo(AppScope::class)
class NetworkModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
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
    @SingleInstanceIn(AppScope::class)
    fun providesMatchingAttributePluginPoint(customConfigs: Set<@JvmSuppressWildcards MatchingAttributePlugin>): PluginPoint<MatchingAttributePlugin> {
        return MatchingAttributePluginPoint(customConfigs)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesMessagePluginPoint(customConfigs: Set<@JvmSuppressWildcards MessagePlugin>): PluginPoint<MessagePlugin> {
        return MessagePluginPoint(customConfigs)
    }
}

@Module
@ContributesTo(AppScope::class)
class DataSourceModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigProcessor(
        remoteMessagingConfigJsonParser: RemoteMessagingConfigJsonParser,
        remoteMessagingConfigRepository: RemoteMessagingConfigRepository
    ): RemoteMessagingConfigProcessor {
        return RealRemoteMessagingConfigProcessor(
            remoteMessagingConfigJsonParser,
            remoteMessagingConfigRepository
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigJsonParser(
        messagePluginPoint: PluginPoint<MessagePlugin>,
        matchingAttributePluginPoint: PluginPoint<MatchingAttributePlugin>
    ): RemoteMessagingConfigJsonParser {
        return RemoteMessagingConfigJsonParser(
            messagePluginPoint,
            matchingAttributePluginPoint
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigRepository(database: RemoteMessagingDatabase): RemoteMessagingConfigRepository {
        return LocalRemoteMessagingConfigRepository(database)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingDatabase(context: Context): RemoteMessagingDatabase {
        return Room.databaseBuilder(context, RemoteMessagingDatabase::class.java, "remote_messaging.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }
}
