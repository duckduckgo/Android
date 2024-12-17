/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.duckchat.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface DuckChatFeatureRepository {
    fun setShowInBrowserMenu(showDuckChat: Boolean)
    fun observeShowInBrowserMenu(): Flow<Boolean>
    fun shouldShowInBrowserMenu(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckChatFeatureRepository @Inject constructor(
    private val duckChatDataStore: DuckChatDataStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckChatFeatureRepository {

    override fun setShowInBrowserMenu(showDuckChat: Boolean) {
        appCoroutineScope.launch {
            duckChatDataStore.setShowInBrowserMenu(showDuckChat)
        }
    }

    override fun observeShowInBrowserMenu(): Flow<Boolean> {
        return duckChatDataStore.observeShowInBrowserMenu()
    }

    override fun shouldShowInBrowserMenu(): Boolean {
        return duckChatDataStore.getShowInBrowserMenu()
    }
}
