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

package com.duckduckgo.cookies.impl.mode

import android.annotation.SuppressLint
import android.webkit.CookieManager
import androidx.annotation.UiThread
import androidx.webkit.ProfileStore
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.browsermode.api.profileName
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Resolves a per-mode [CookieManager] (mirrors `WebStorageProvider`). Call [forMode] on the main
 * thread when MultiProfile is supported — `ProfileStore` is tied to the WebView thread. Falls back to
 * the default global [CookieManager] when Fire mode is unavailable.
 */
@SuppressLint("RequiresFeature")
@SingleInstanceIn(AppScope::class)
class CookieManagerModeProvider @Inject constructor(
    private val fireModeAvailability: FireModeAvailability,
) : BrowserModeDataProvider<CookieManager> {

    @UiThread
    override fun forMode(mode: BrowserMode): CookieManager {
        if (!fireModeAvailability.isAvailable()) return CookieManager.getInstance()
        return ProfileStore.getInstance().getOrCreateProfile(mode.profileName).cookieManager
    }
}

@ContributesTo(AppScope::class)
@Module
abstract class CookieManagerModeProviderModule {
    @Binds
    abstract fun bindCookieManagerModeProvider(impl: CookieManagerModeProvider): BrowserModeDataProvider<CookieManager>
}
