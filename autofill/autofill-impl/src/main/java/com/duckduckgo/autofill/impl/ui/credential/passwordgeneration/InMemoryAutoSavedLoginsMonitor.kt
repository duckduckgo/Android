/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.passwordgeneration

import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

// singleton in app scope, as we want the same instance across multiple fragments
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class InMemoryAutoSavedLoginsMonitor @Inject constructor() : AutomaticSavedLoginsMonitor {

    private val autoSavedLoginsPerTab = mutableMapOf<String, Long>()

    override fun getAutoSavedLoginId(tabId: String?): Long? = autoSavedLoginsPerTab[tabId]

    override fun setAutoSavedLoginId(
        value: Long,
        tabId: String?,
    ) {
        tabId?.let { autoSavedLoginsPerTab[it] = value }
    }

    override fun clearAutoSavedLoginId(tabId: String?) {
        tabId?.let {
            autoSavedLoginsPerTab.remove(it)
        }
    }
}
