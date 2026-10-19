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

package com.duckduckgo.app.browser.session

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
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
import kotlinx.coroutines.CoroutineScope

/**
 * Each mode keeps its sessions in its own database, so a Fire tab's session can't outlive the Fire tab rows.
 */
@ContributesTo(AppScope::class)
@Module
object WebViewSessionStorageModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    @RegularMode
    fun provideRegularWebViewSessionStorage(
        @RegularMode dao: WebViewSessionDao,
        @AppCoroutineScope appScope: CoroutineScope,
        dispatchers: DispatcherProvider,
        pixel: Pixel,
        browserConfig: AndroidBrowserConfigFeature,
    ): WebViewSessionStorage = WebViewSessionStorageProxy(
        RoomWebViewSessionStorage(dao, appScope, dispatchers, pixel),
        WebViewSessionInMemoryStorage(),
        browserConfig,
    )

    @Provides
    @SingleInstanceIn(AppScope::class)
    @FireMode
    fun provideFireWebViewSessionStorage(
        @FireMode dao: WebViewSessionDao,
        @AppCoroutineScope appScope: CoroutineScope,
        dispatchers: DispatcherProvider,
        pixel: Pixel,
        browserConfig: AndroidBrowserConfigFeature,
    ): WebViewSessionStorage = WebViewSessionStorageProxy(
        RoomWebViewSessionStorage(dao, appScope, dispatchers, pixel),
        WebViewSessionInMemoryStorage(),
        browserConfig,
    )
}

/**
 * The unqualified [WebViewSessionStorage] is bound only in [ActivityScope], resolved from the activity's frozen
 * [BrowserMode], so a save during a mode-switch recreate can't land in the other mode's database.
 * AppScope consumers must inject the qualified `@RegularMode` / `@FireMode` storage explicitly.
 */
@ContributesTo(ActivityScope::class)
@Module
object WebViewSessionStorageActivityModule {

    @Provides
    fun provideCurrentModeWebViewSessionStorage(
        @RegularMode regular: WebViewSessionStorage,
        @FireMode fire: WebViewSessionStorage,
        activityMode: BrowserMode,
    ): WebViewSessionStorage = when (activityMode) {
        BrowserMode.REGULAR -> regular
        BrowserMode.FIRE -> fire
    }
}
