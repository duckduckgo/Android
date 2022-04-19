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

package com.duckduckgo.voice.impl.di

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.voice.store.RealVoiceSearchRepository
import com.duckduckgo.voice.store.SharedPreferencesVoiceSearchDataStore
import com.duckduckgo.voice.store.VoiceSearchDataStore
import com.duckduckgo.voice.store.VoiceSearchRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
object VoiceSearchModule {
    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideVoiceSearchRepository(dataStore: VoiceSearchDataStore): VoiceSearchRepository {
        return RealVoiceSearchRepository(dataStore)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideVoiceSearchDataStore(context: Context): VoiceSearchDataStore {
        return SharedPreferencesVoiceSearchDataStore(context)
    }
}
