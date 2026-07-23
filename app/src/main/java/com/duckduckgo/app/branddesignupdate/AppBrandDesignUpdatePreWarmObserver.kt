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

package com.duckduckgo.app.branddesignupdate

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pre-warms the `appBrandDesignUpdate` toggle's in-memory cache off the main thread so the
 * synchronous read in `DuckDuckGoActivity.onCreate` is a cache hit instead of a first-read disk hit.
 *
 * Interim scaffolding for the brand design update. The follow-up theme project deletes this observer
 * with the flag.
 */
@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AppBrandDesignUpdatePreWarmObserver @Inject constructor(
    private val toggles: AppBrandDesignUpdateToggles,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            toggles.feature().isEnabled()
        }
    }
}
