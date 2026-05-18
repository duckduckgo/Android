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

import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

/**
 * Unqualified [TabRepository] and [BrowserMode] is intentionally bound only in [ActivityScope],
 * not [AppScope].
 *
 * Activity- and fragment-scoped consumers (BrowserViewModel, BrowserTabViewModel,
 * OmnibarLayoutViewModel, etc.) are recreated whenever the browser mode changes, so each new
 * instance resolves the right repo and mode at construction time.
 */
@ContributesTo(ActivityScope::class)
@Module
class TabRepositoryActivityModule {
    /**
     * The activity's [BrowserMode] is captured **once** at activity-component creation
     * (`@SingleInstanceIn(ActivityScope::class)` on [provideActivityBrowserMode]) and reused for
     * every unqualified [TabRepository] resolution within that activity. This guarantees a frozen
     * mode for the activity's lifetime even if [BrowserModeStateHolder.currentMode] changes
     * mid-construction — the activity recreates on real mode changes, so the next instance
     * captures the new value cleanly.
     */
    @Provides
    @SingleInstanceIn(ActivityScope::class)
    fun provideActivityBrowserMode(browserModeStateHolder: BrowserModeStateHolder): BrowserMode =
        browserModeStateHolder.currentMode.value

    /**
     * AppScope-singleton consumers cannot reach this binding — they must inject the qualified
     * [@RegularMode] or [@FireMode] variants (or both) explicitly, which is what they want anyway:
     * cross-mode work like data clearing operates on both, and one-shot app-launch logic should
     * pick a specific mode rather than silently follow whatever the user last toggled.
     */
    @Provides
    fun provideUnqualifiedTabRepository(
        @RegularMode regular: TabRepository,
        @FireMode fire: TabRepository,
        activityMode: BrowserMode,
    ): TabRepository = when (activityMode) {
        BrowserMode.REGULAR -> regular
        BrowserMode.FIRE -> fire
    }
}
