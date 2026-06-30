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

package com.duckduckgo.sync.impl.promotion.chat

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.ChatTabPage
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface ChatSyncPromotion {
    suspend fun canShowPromotion(): Boolean
    suspend fun incrementImpressionCount()
    suspend fun recordPromotionAccepted()
    suspend fun recordPromotionDismissed()
}

@ContributesBinding(AppScope::class, boundType = ChatSyncPromotion::class)
class RealChatSyncPromotion @Inject constructor(
    private val promotionDataStore: SyncPromotionDataStore,
    private val syncState: DeviceSyncState,
    private val duckChat: DuckChat,
    private val dispatchers: DispatcherProvider,
) : ChatSyncPromotion {
    override suspend fun canShowPromotion(): Boolean = coroutineScope {
        val isAvailable = async { !isPromoExhausted() }
        val isEligible = async { isEligibleForPromo() }
        return@coroutineScope isAvailable.await() && isEligible.await()
    }

    override suspend fun incrementImpressionCount() {
        promotionDataStore.recordPromoImpression(ChatTabPage)
    }

    override suspend fun recordPromotionAccepted() {
        promotionDataStore.recordPromoDismissed(ChatTabPage)
    }

    override suspend fun recordPromotionDismissed() {
        promotionDataStore.recordPromoDismissed(ChatTabPage)
    }

    private suspend fun isPromoExhausted(): Boolean {
        return promotionDataStore.hasPromoBeenDismissed(ChatTabPage) ||
            promotionDataStore.getPromoImpressionCount(ChatTabPage) >= MAX_IMPRESSION_COUNT
    }

    private suspend fun isEligibleForPromo() = coroutineScope {
        val preconditions = listOf(
            async { canTurnOnSync() },
            async { hasChatHistoryEnabled() },
            async { hasChatSuggestions() },
        ).awaitAll()
        return@coroutineScope preconditions.all { it }
    }

    private suspend fun canTurnOnSync(): Boolean = withContext(dispatchers.io()) {
        return@withContext syncState.isFeatureEnabled() &&
            syncState.isDuckChatSyncFeatureEnabled() &&
            !syncState.isUserSignedInOnDevice()
    }

    private suspend fun hasChatHistoryEnabled(): Boolean = duckChat.hasUserEnabledChatHistory()

    private suspend fun hasChatSuggestions(): Boolean = duckChat.observeHasChatSuggestions().firstOrNull() == true

    companion object {
        const val MAX_IMPRESSION_COUNT = 3
    }
}
