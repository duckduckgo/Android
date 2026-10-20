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

package com.duckduckgo.duckchat.impl.promo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.updateAndGet

class FakePromoCampaignDataStore : PromoCampaignDataStore {
    val record = MutableStateFlow<PromoRecord?>(null)
    val readFailures = ArrayDeque<Throwable>()
    var reads = 0
        private set

    override val promoRecord: Flow<PromoRecord?> = flow {
        reads++
        readFailures.removeFirstOrNull()?.let { throw it }
        emitAll(record)
    }

    override suspend fun getPromoRecord(): PromoRecord? = record.value

    override suspend fun updatePromoRecord(transform: (PromoRecord?) -> PromoRecord?): PromoRecord? = record.updateAndGet(transform)

    override suspend fun clear() {
        record.value = null
    }
}
