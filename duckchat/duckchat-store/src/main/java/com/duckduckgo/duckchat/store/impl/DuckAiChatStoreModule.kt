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

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

/**
 * Provides the per-mode [RealDuckAiChatStore] instances bound to the qualified [DuckAiChatStore]. Migration
 * prefs are shared across modes (one-time, app-level event).
 */
@ContributesTo(AppScope::class)
@Module
object DuckAiChatStoreModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    @RegularMode
    fun provideRegularChatStore(
        @RegularMode storage: DuckAiBridgeStorage,
        dispatchers: DispatcherProvider,
        migrationPrefs: DuckAiMigrationPrefs,
    ): DuckAiChatStore = RealDuckAiChatStore(storage, dispatchers, migrationPrefs)

    @Provides
    @SingleInstanceIn(AppScope::class)
    @FireMode
    fun provideFireChatStore(
        @FireMode storage: DuckAiBridgeStorage,
        dispatchers: DispatcherProvider,
        migrationPrefs: DuckAiMigrationPrefs,
    ): DuckAiChatStore = RealDuckAiChatStore(storage, dispatchers, migrationPrefs)
}

/**
 * The unqualified [DuckAiChatStore] is intentionally bound only in [ActivityScope]: it resolves to the
 * qualified store of the activity's frozen [BrowserMode], captured once at activity-component creation.
 * AppScope consumers must inject the qualified `@RegularMode` / `@FireMode` stores explicitly.
 */
@ContributesTo(ActivityScope::class)
@Module
object DuckAiChatStoreActivityModule {

    @Provides
    fun provideCurrentModeChatStore(
        @RegularMode regular: DuckAiChatStore,
        @FireMode fire: DuckAiChatStore,
        activityMode: BrowserMode,
    ): DuckAiChatStore = when (activityMode) {
        BrowserMode.REGULAR -> regular
        BrowserMode.FIRE -> fire
    }
}
