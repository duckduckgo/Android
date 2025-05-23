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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.*
import com.duckduckgo.remote.messaging.impl.mappers.MessageMapper
import com.duckduckgo.remote.messaging.impl.mappers.RemoteMessagingConfigJsonMapper
import com.duckduckgo.remote.messaging.impl.matchers.AndroidAppAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.DeviceAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.UserAttributeMatcher
import com.duckduckgo.remote.messaging.impl.network.RemoteMessagingService
import com.duckduckgo.remote.messaging.store.LocalRemoteMessagingConfigRepository
import com.duckduckgo.remote.messaging.store.RemoteMessagesDao
import com.duckduckgo.remote.messaging.store.RemoteMessagingCohortStore
import com.duckduckgo.remote.messaging.store.RemoteMessagingCohortStoreImpl
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import com.duckduckgo.remote.messaging.store.RemoteMessagingDatabase
import com.duckduckgo.remote.messaging.store.RemoteMessagingDatabase.Companion.ALL_MIGRATIONS
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet

@Module
@ContributesTo(AppScope::class)
object DomainModule {

    @Provides
    fun providesRemoteMessagingConfigDownloader(
        remoteConfig: RemoteMessagingService,
        remoteMessagingConfigProcessor: RemoteMessagingConfigProcessor,
    ): RemoteMessagingConfigDownloader {
        return RealRemoteMessagingConfigDownloader(remoteConfig, remoteMessagingConfigProcessor)
    }
}

@Module
@ContributesTo(AppScope::class)
object DataSourceModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigProcessor(
        remoteMessagingConfigJsonMapper: RemoteMessagingConfigJsonMapper,
        remoteMessagingConfigRepository: RemoteMessagingConfigRepository,
        remoteMessagingRepository: RemoteMessagingRepository,
        remoteMessagingConfigMatcher: RemoteMessagingConfigMatcher,
        remoteMessagingFeatureToggles: RemoteMessagingFeatureToggles,
    ): RemoteMessagingConfigProcessor {
        return RealRemoteMessagingConfigProcessor(
            remoteMessagingConfigJsonMapper,
            remoteMessagingConfigRepository,
            remoteMessagingRepository,
            remoteMessagingConfigMatcher,
            remoteMessagingFeatureToggles,
        )
    }

    @Provides
    fun providesRemoteMessagingRepository(
        remoteMessagingConfigRepository: RemoteMessagingConfigRepository,
        remoteMessagesDao: RemoteMessagesDao,
        dispatchers: DispatcherProvider,
        messageMapper: MessageMapper,
    ): RemoteMessagingRepository {
        return AppRemoteMessagingRepository(
            remoteMessagingConfigRepository,
            remoteMessagesDao,
            dispatchers,
            messageMapper,
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagesDao(
        remoteMessagingDatabase: RemoteMessagingDatabase,
    ): RemoteMessagesDao {
        return remoteMessagingDatabase.remoteMessagesDao()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigJsonMapper(
        matchingAttributeMappers: DaggerSet<JsonToMatchingAttributeMapper>,
        actionMappers: DaggerSet<MessageActionMapperPlugin>,
        appBuildConfig: AppBuildConfig,
    ): RemoteMessagingConfigJsonMapper {
        return RemoteMessagingConfigJsonMapper(appBuildConfig, matchingAttributeMappers, actionMappers)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingConfigMatcher(
        matchers: DaggerSet<AttributeMatcherPlugin>,
        remoteMessagingRepository: RemoteMessagingRepository,
        remoteMessagingCohortStore: RemoteMessagingCohortStore,
    ): RemoteMessagingConfigMatcher {
        return RemoteMessagingConfigMatcher(matchers, remoteMessagingRepository, remoteMessagingCohortStore)
    }

    @Provides
    @IntoSet
    fun providesAndroidAppAttributeMatcher(
        appProperties: AppProperties,
        appBuildConfig: AppBuildConfig,
    ): AttributeMatcherPlugin {
        return AndroidAppAttributeMatcher(appProperties, appBuildConfig)
    }

    @Provides
    @IntoSet
    fun providesDeviceAttributeMatcher(
        appBuildConfig: AppBuildConfig,
        appProperties: AppProperties,
    ): AttributeMatcherPlugin {
        return DeviceAttributeMatcher(appBuildConfig, appProperties)
    }

    @Provides
    @IntoSet
    fun providesUserAttributeMatcher(
        userBrowserProperties: UserBrowserProperties,
    ): AttributeMatcherPlugin {
        return UserAttributeMatcher(userBrowserProperties)
    }

    @Provides
    fun providesRemoteMessagingConfigRepository(database: RemoteMessagingDatabase): RemoteMessagingConfigRepository {
        return LocalRemoteMessagingConfigRepository(database)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingDatabase(context: Context): RemoteMessagingDatabase {
        return Room.databaseBuilder(context, RemoteMessagingDatabase::class.java, "remote_messaging.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesRemoteMessagingUserDataStore(database: RemoteMessagingDatabase, dispatchers: DispatcherProvider): RemoteMessagingCohortStore {
        return RemoteMessagingCohortStoreImpl(database, dispatchers)
    }
}
