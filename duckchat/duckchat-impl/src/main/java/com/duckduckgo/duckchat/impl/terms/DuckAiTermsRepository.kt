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

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Duck.ai ToS acceptance, shared with the frontend through the native storage setting it already reads.
 */
interface DuckAiTermsRepository {
    fun observeTermsAccepted(browserMode: BrowserMode): Flow<Boolean>

    suspend fun markTermsAccepted(browserMode: BrowserMode)
}

@ContributesBinding(AppScope::class)
class RealDuckAiTermsRepository @Inject constructor(
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage>,
    private val dispatchers: DispatcherProvider,
) : DuckAiTermsRepository {

    override fun observeTermsAccepted(browserMode: BrowserMode): Flow<Boolean> =
        storageProvider.forMode(browserMode).settings
            .observe(TERMS_KEY)
            .map { it?.value == ACCEPTED }
            .distinctUntilChanged()
            .flowOn(dispatchers.io())

    override suspend fun markTermsAccepted(browserMode: BrowserMode) {
        withContext(dispatchers.io()) {
            storageProvider.forMode(browserMode).settings.upsert(DuckAiBridgeSettingEntity(key = TERMS_KEY, value = ACCEPTED))
        }
    }

    private companion object {
        const val TERMS_KEY = "duckaiHasAgreedToTerms"
        const val ACCEPTED = "true"
    }
}
