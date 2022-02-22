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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.remote.messaging.impl.RealRemoteMessagingConfigDownloader
import com.duckduckgo.remote.messaging.impl.RemoteMessagingConfigDownloader
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.*
import com.duckduckgo.remote.messaging.impl.mappers.RemoteMessagingConfigJsonMapper
import com.duckduckgo.remote.messaging.impl.matchers.AndroidAppAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.DeviceAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.UserAttributeMatcher
import com.duckduckgo.remote.messaging.impl.network.RemoteMessagingService
import com.duckduckgo.remote.messaging.store.ALL_MIGRATIONS
import com.duckduckgo.remote.messaging.store.LocalRemoteMessagingConfigRepository
import com.duckduckgo.remote.messaging.store.RemoteMessagesDao
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
        remoteMessagingRepository: RemoteMessagingRepository,
        remoteMessagingConfigMatcher: RemoteMessagingConfigMatcher
    ): RemoteMessagingConfigProcessor {
        return RealRemoteMessagingConfigProcessor(
            remoteMessagingConfigJsonMapper,
            remoteMessagingConfigRepository,
            remoteMessagingRepository,
            remoteMessagingConfigMatcher
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingRepository(
        remoteMessagingConfigRepository: RemoteMessagingConfigRepository,
        remoteMessagesDao: RemoteMessagesDao,
        dispatchers: DispatcherProvider
    ): RemoteMessagingRepository {
        return AppRemoteMessagingRepository(remoteMessagingConfigRepository, remoteMessagesDao, dispatchers)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagesDao(
        remoteMessagingDatabase: RemoteMessagingDatabase
    ): RemoteMessagesDao {
        return remoteMessagingDatabase.remoteMessagesDao()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigJsonMapper(
        appBuildConfig: AppBuildConfig
    ): RemoteMessagingConfigJsonMapper {
        return RemoteMessagingConfigJsonMapper(appBuildConfig)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigMatcher(
        deviceAttributeMatcher: DeviceAttributeMatcher,
        androidAppAttributeMatcher: AndroidAppAttributeMatcher,
        remoteMessagingRepository: RemoteMessagingRepository,
        userAttributeMatcher: UserAttributeMatcher
    ): RemoteMessagingConfigMatcher {
        return RemoteMessagingConfigMatcher(deviceAttributeMatcher, androidAppAttributeMatcher, remoteMessagingRepository, userAttributeMatcher)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesAndroidAppAttributeMatcher(
        appProperties: AppProperties,
        appBuildConfig: AppBuildConfig
    ): AndroidAppAttributeMatcher {
        return AndroidAppAttributeMatcher(appProperties, appBuildConfig)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesDeviceAttributeMatcher(
        appBuildConfig: AppBuildConfig,
        appProperties: AppProperties
    ): DeviceAttributeMatcher {
        return DeviceAttributeMatcher(appBuildConfig, appProperties)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesUserAttributeMatcher(
        userBrowserProperties: UserBrowserProperties
    ): UserAttributeMatcher {
        return UserAttributeMatcher(userBrowserProperties)
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
