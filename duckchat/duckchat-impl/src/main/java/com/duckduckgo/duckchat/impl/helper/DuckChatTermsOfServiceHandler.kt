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
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

interface DuckChatTermsOfServiceHandler {
    suspend fun userAcceptedTerms()
}

@ContributesBinding(AppScope::class)
class RealDuckChatTermsOfServiceHandler @Inject constructor(
    private val dataStore: DuckChatDataStore,
    private val duckChatPixels: DuckChatPixels,
    private val duckChat: DuckChatInternal,
) : DuckChatTermsOfServiceHandler {

    override suspend fun userAcceptedTerms() {
        logcat { "Duck.ai: userAcceptedTerms" }
        if (dataStore.hasUserAcceptedTerms()) {
            logcat { "Duck.ai: userAcceptedTerms DUPLICATE" }
            val isSyncEnabled = duckChat.isChatSyncFeatureEnabled()
            duckChatPixels.reportTermsOfServiceReAccepted(isSyncEnabled)
        }
        dataStore.setUserAcceptedTerms()
    }
}
