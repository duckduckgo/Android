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

package com.duckduckgo.app.autocomplete.impl

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutoCompleteRepository {

    fun countHistoryInAutoCompleteIAMShown(): Int
    fun dismissHistoryInAutoCompleteIAM()
    fun wasHistoryInAutoCompleteIAMDismissed(): Boolean
    fun submitUserSeenHistoryIAM()
}

@ContributesBinding(AppScope::class)
class RealAutoCompleteRepository @Inject constructor(
    private val dataStore: AutoCompleteDataStore,
) : AutoCompleteRepository {

    override fun countHistoryInAutoCompleteIAMShown(): Int {
        return dataStore.countHistoryInAutoCompleteIAMShown
    }

    override fun dismissHistoryInAutoCompleteIAM() {
        dataStore.setHistoryInAutoCompleteIAMDismissed()
    }

    override fun wasHistoryInAutoCompleteIAMDismissed(): Boolean {
        return dataStore.wasHistoryInAutoCompleteIAMDismissed()
    }

    override fun submitUserSeenHistoryIAM() {
        dataStore.countHistoryInAutoCompleteIAMShown += 1
    }
}
