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

package com.duckduckgo.app.fire

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.promo.FireTabsPromos
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class FireTabsPromoModeObserver @Inject constructor(
    private val browserModeStateHolder: BrowserModeStateHolder,
    private val fireTabsPromos: FireTabsPromos,
    @param:AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    // Mode-transition observer: fires once on the first entry into Fire mode.
    @Suppress("DenyListedApi")
    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            browserModeStateHolder.currentMode
                .filter { it == BrowserMode.FIRE }
                .take(1)
                .collect { fireTabsPromos.onFireModeEntered() }
        }
    }
}
