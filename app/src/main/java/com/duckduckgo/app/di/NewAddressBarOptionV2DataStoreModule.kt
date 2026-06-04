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
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Qualifier

@ContributesTo(AppScope::class)
@Module
object NewAddressBarOptionV2DataStoreModule {

    private val Context.newAddressBarOptionV2DataStore: DataStore<Preferences> by preferencesDataStore(
        name = "new_address_bar_option_v2",
    )

    @Provides
    @NewAddressBarOptionV2
    fun provideNewAddressBarOptionV2DataStore(context: Context): DataStore<Preferences> = context.newAddressBarOptionV2DataStore
}

@Qualifier
internal annotation class NewAddressBarOptionV2
