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
package com.duckduckgo.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import javax.inject.Qualifier

@ContributesTo(AppScope::class)
@BindingContainer
object DuckAiToggleMigrationDataStoreModule {

    private val Context.duckAiToggleMigrationDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "duck_ai_toggle_migration",
    )

    // Written by the V1 address bar picker, which no longer exists. Kept on disk on devices that saw it, and only read now.
    private val Context.legacyAddressBarOptionDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "new_address_bar_option",
    )

    @Provides
    @DuckAiToggleMigration
    fun provideDuckAiToggleMigrationDataStore(context: Context): DataStore<Preferences> = context.duckAiToggleMigrationDataStore

    @Provides
    @LegacyAddressBarOption
    fun provideLegacyAddressBarOptionDataStore(context: Context): DataStore<Preferences> = context.legacyAddressBarOptionDataStore
}

@Qualifier
internal annotation class DuckAiToggleMigration

@Qualifier
internal annotation class LegacyAddressBarOption
