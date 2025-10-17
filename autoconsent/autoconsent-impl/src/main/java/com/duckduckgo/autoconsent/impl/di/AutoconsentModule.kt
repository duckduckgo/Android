/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelManager
import com.duckduckgo.autoconsent.impl.pixels.RealAutoconsentPixelManager
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.autoconsent.impl.store.AutoconsentDatabase
import com.duckduckgo.autoconsent.impl.store.AutoconsentSettingsRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@ContributesTo(AppScope::class)
@Module
object AutoconsentModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAutoconsentSettingsRepository(
        context: Context,
        autoconsentFeature: AutoconsentFeature,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): AutoconsentSettingsRepository {
        return AutoconsentSettingsRepository.create(context, autoconsentFeature, appCoroutineScope, dispatcherProvider)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAutoconsentDatabase(context: Context): AutoconsentDatabase {
        return Room.databaseBuilder(context, AutoconsentDatabase::class.java, "autoconsent.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAutoconsentPixelManager(realAutoconsentPixelManager: RealAutoconsentPixelManager): AutoconsentPixelManager {
        return realAutoconsentPixelManager
    }
}
