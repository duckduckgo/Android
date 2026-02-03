/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.features.browseruilock

import com.duckduckgo.contentscopescripts.impl.features.browseruilock.store.BrowserUiLockStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface BrowserUiLockRepository {
    fun updateAll(json: String)
    fun getJsonData(): String
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrowserUiLockRepository @Inject constructor(
    private val browserUiLockStore: BrowserUiLockStore,
) : BrowserUiLockRepository {

    override fun updateAll(json: String) {
        browserUiLockStore.json = json
    }

    override fun getJsonData(): String {
        return browserUiLockStore.json
    }
}
