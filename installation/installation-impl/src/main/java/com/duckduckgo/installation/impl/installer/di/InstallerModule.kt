/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.installation.impl.installer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import javax.inject.Qualifier

@Module
@ContributesTo(AppScope::class)
object InstallerModule {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "com.duckduckgo.installation.impl.installer",
    )

    @Provides
    @SingleInstanceIn(AppScope::class)
    @InstallSourceFullPackageDataStore
    fun provideInstallSourceFullPackageDataStore(context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Qualifier
    annotation class InstallSourceFullPackageDataStore
}
