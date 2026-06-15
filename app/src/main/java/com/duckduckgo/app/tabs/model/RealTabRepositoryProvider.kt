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

package com.duckduckgo.app.tabs.model

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Resolves a per-mode [TabRepository]. Delegates to the [RegularMode] or [FireMode] repository
 * based on the active [BrowserMode].
 */
@SingleInstanceIn(AppScope::class)
class RealTabRepositoryProvider @Inject constructor(
    @RegularMode private val regularRepo: TabRepository,
    @FireMode private val fireRepo: TabRepository,
) : BrowserModeDataProvider<TabRepository> {

    override fun forMode(mode: BrowserMode): TabRepository = when (mode) {
        BrowserMode.REGULAR -> regularRepo
        BrowserMode.FIRE -> fireRepo
    }
}

@ContributesTo(AppScope::class)
@Module
abstract class RealTabRepositoryProviderModule {
    @Binds
    abstract fun bindTabRepositoryProvider(impl: RealTabRepositoryProvider): BrowserModeDataProvider<TabRepository>
}
