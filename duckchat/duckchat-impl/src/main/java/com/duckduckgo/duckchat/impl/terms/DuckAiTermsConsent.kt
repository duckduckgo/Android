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
package com.duckduckgo.duckchat.impl.terms

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiTermsConsent
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDuckAiTermsConsent @Inject constructor(
    private val duckChatFeature: DuckChatFeature,
    private val termsRepository: DuckAiTermsRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckAiTermsConsent {

    override fun carry(
        params: JSONObject,
        browserMode: BrowserMode,
    ) {
        if (!duckChatFeature.nativeToSConsent().isEnabled()) return
        params.put(TERMS_ACCEPTED, true)
        // The sender is not suspending and the page already has the flag, so the write must not hold the prompt up.
        appCoroutineScope.launch { termsRepository.markTermsAccepted(browserMode) }
    }

    private companion object {
        const val TERMS_ACCEPTED = "termsAccepted"
    }
}
