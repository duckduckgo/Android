/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.webinterferencedetection.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.webinterferencedetection.store.ALL_MIGRATIONS
import com.duckduckgo.webinterferencedetection.store.RealWebInterferenceDetectionRepository
import com.duckduckgo.webinterferencedetection.store.WebInterferenceDetectionDatabase
import com.duckduckgo.webinterferencedetection.store.WebInterferenceDetectionRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object WebInterferenceDetectionModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideWebInterferenceDetectionDatabase(context: Context): WebInterferenceDetectionDatabase {
        return Room.databaseBuilder(context, WebInterferenceDetectionDatabase::class.java, "web_interference_detection.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideWebInterferenceDetectionRepository(
        database: WebInterferenceDetectionDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): WebInterferenceDetectionRepository {
        return RealWebInterferenceDetectionRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }
}
