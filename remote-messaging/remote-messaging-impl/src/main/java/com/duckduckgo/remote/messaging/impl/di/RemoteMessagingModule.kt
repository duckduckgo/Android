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

package com.duckduckgo.remote.messaging.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.remote.messaging.impl.mappers.JsonRemoteMessageMapper
import com.duckduckgo.remote.messaging.impl.mappers.JsonRulesMapper
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.impl.*
import com.duckduckgo.remote.messaging.impl.mappers.RemoteMessagingConfigJsonMapper
import com.duckduckgo.remote.messaging.impl.network.RemoteMessagingService
import com.duckduckgo.remote.messaging.store.ALL_MIGRATIONS
import com.duckduckgo.remote.messaging.store.LocalRemoteMessagingConfigRepository
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import com.duckduckgo.remote.messaging.store.RemoteMessagingDatabase
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named

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
        //val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(AppUrl.Url.API)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(RemoteMessagingService::class.java)
    }
}

@Module
@ContributesTo(AppScope::class)
class DataSourceModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigProcessor(
        remoteMessagingConfigJsonMapper: RemoteMessagingConfigJsonMapper,
        remoteMessagingConfigRepository: RemoteMessagingConfigRepository,
        remoteMessagingConfigMatcher: RemoteMessagingConfigMatcher
    ): RemoteMessagingConfigProcessor {
        return RealRemoteMessagingConfigProcessor(
            remoteMessagingConfigJsonMapper,
            remoteMessagingConfigRepository,
            remoteMessagingConfigMatcher
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigJsonMapper(
        jsonRemoteMessageMapper: JsonRemoteMessageMapper,
        jsonRulesMapper: JsonRulesMapper
    ): RemoteMessagingConfigJsonMapper {
        return RemoteMessagingConfigJsonMapper(jsonRemoteMessageMapper, jsonRulesMapper)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesJsonRemoteMessageMapper(): JsonRemoteMessageMapper {
        return JsonRemoteMessageMapper()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesJsonRulesMapper(): JsonRulesMapper {
        return JsonRulesMapper()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigMatcher(): RemoteMessagingConfigMatcher {
        return RemoteMessagingConfigMatcher()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigRepository(database: RemoteMessagingDatabase): com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository {
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
