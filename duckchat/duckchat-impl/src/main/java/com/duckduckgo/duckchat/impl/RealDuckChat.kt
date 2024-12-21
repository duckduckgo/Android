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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface DuckChatInternal : DuckChat {
    /**
     * Stores setting to determine whether the DuckChat should be shown in browser menu
     */
    fun setShowInBrowserMenu(showDuckChat: Boolean)

    fun observeShowInBrowserMenu(): Flow<Boolean>
}

@ContributesBinding(AppScope::class, boundType = DuckChat::class)
@ContributesBinding(AppScope::class, boundType = DuckChatInternal::class)
class RealDuckChat @Inject constructor(
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
) : DuckChatInternal {
    override fun setShowInBrowserMenu(showDuckChat: Boolean) {
        duckChatFeatureRepository.setShowInBrowserMenu(showDuckChat)
    }

    override fun observeShowInBrowserMenu(): Flow<Boolean> {
        return duckChatFeatureRepository.observeShowInBrowserMenu()
    }

    override fun showInBrowserMenu(): Boolean {
        return duckChatFeatureRepository.shouldShowInBrowserMenu()
    }
}
