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

package com.duckduckgo.duckchat.impl.nativeinput

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.terms.DuckAiTermsRepository
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject

/**
 * Hints are only for returning users, so they stay hidden while the frontend still shows its own first-run pills,
 * and once [NewChatHintsUsage] says the user keeps prompting without them.
 */
interface NewChatHintsEligibility {
    fun observeEligible(browserMode: BrowserMode): Flow<Boolean>
}

@ContributesBinding(AppScope::class)
class RealNewChatHintsEligibility @Inject constructor(
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage>,
    private val termsRepository: DuckAiTermsRepository,
    private val usage: NewChatHintsUsage,
    private val dispatchers: DispatcherProvider,
) : NewChatHintsEligibility {

    override fun observeEligible(browserMode: BrowserMode): Flow<Boolean> {
        // A fresh Fire session has empty storage, so a prompt sent in Regular mode still counts as returning.
        val isReturningUser = combine(setOf(BrowserMode.REGULAR, browserMode).map(::hasPromptedIn)) { prompted -> prompted.any { it } }
        return combine(
            isReturningUser,
            termsRepository.observeTermsAccepted(browserMode),
            usage.isExhausted,
        ) { returning, accepted, exhausted ->
            returning && accepted && !exhausted
        }
            .distinctUntilChanged()
    }

    private fun hasPromptedIn(browserMode: BrowserMode): Flow<Boolean> = storageProvider.forMode(browserMode).settings
        .observe(CONVERSIONS_KEY)
        .map { it?.value.hasConversion(PROMPT_CONVERSION) }
        .flowOn(dispatchers.io())

    private fun String?.hasConversion(conversion: String): Boolean {
        if (this == null) return false
        val conversions = runCatching { JSONArray(this) }.getOrNull() ?: return false
        return (0 until conversions.length()).any { conversions.optString(it) == conversion }
    }

    private companion object {
        const val CONVERSIONS_KEY = "origin_conversions"
        const val PROMPT_CONVERSION = "duckai_prompt"
    }
}
