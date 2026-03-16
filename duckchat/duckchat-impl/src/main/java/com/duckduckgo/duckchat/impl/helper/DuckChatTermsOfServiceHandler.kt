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

package com.duckduckgo.duckchat.impl.helper

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

data class TermsAcceptanceResult(
    val isDuplicate: Boolean,
    val isSyncEnabled: Boolean,
)

interface DuckChatTermsOfServiceHandler {
    suspend fun userAcceptedTerms(): TermsAcceptanceResult
}

@ContributesBinding(AppScope::class)
class RealDuckChatTermsOfServiceHandler @Inject constructor(
    private val dataStore: DuckChatDataStore,
    private val duckChat: DuckChatInternal,
) : DuckChatTermsOfServiceHandler {

    override suspend fun userAcceptedTerms(): TermsAcceptanceResult {
        val alreadyAccepted = dataStore.hasUserAcceptedTerms()
        val isSyncEnabled = duckChat.isChatSyncFeatureEnabled()
        if (alreadyAccepted) {
        }
        dataStore.setUserAcceptedTerms()
        return TermsAcceptanceResult(isDuplicate = alreadyAccepted, isSyncEnabled = isSyncEnabled)
    }
}
