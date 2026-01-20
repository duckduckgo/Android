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

package com.duckduckgo.downloads.impl.di

import android.annotation.SuppressLint
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.impl.DownloadFileService
import com.duckduckgo.downloads.store.DownloadsDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import retrofit2.Retrofit
import javax.inject.Named

@Module
@ContributesTo(AppScope::class)
class DownloadsModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideDownloadsDatabase(databaseProvider: DatabaseProvider): DownloadsDatabase {
        return databaseProvider.buildRoomDatabase(
            DownloadsDatabase::class.java,
            "downloads.db",
            config = RoomDatabaseConfig(
                fallbackToDestructiveMigration = true,
            ),
        )
    }

    /**
     * Provides a fallback DownloadFileService that uses HTTP/1.1 only.
     * Used when HTTP/2 fails with StreamResetException on certain servers.
     */
    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("http1Fallback")
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun provideDownloadFileServiceHttp1Fallback(
        @Named("downloadsHttp1") retrofit: Retrofit,
    ): DownloadFileService {
        return retrofit.create(DownloadFileService::class.java)
    }
}
