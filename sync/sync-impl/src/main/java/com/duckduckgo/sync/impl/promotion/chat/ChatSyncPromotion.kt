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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
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
    private val browserModeStateHolder: BrowserModeStateHolder,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) : ChatSyncPromotion {
    override suspend fun canShowPromotion(): Boolean {
        if (browserModeStateHolder.currentMode.value == BrowserMode.FIRE) {
            return false
        }

        return coroutineScope {
            val isAvailable = async { !isPromoExhausted() }
            val isEligible = async { isEligibleForPromo() }
            isAvailable.await() && isEligible.await()
        }
    }

    override suspend fun incrementImpressionCount() {
        promotionDataStore.recordPromoImpression(ChatTabPage)
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, pixelParmas())
    }

    override suspend fun recordPromotionAccepted() {
        promotionDataStore.recordPromoDismissed(ChatTabPage)
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, pixelParmas())
    }

    override suspend fun recordPromotionDismissed() {
        promotionDataStore.recordPromoDismissed(ChatTabPage)
        pixel.fire(
            pixel = SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED,
            parameters = pixelParmas {
                put(SyncPixelParameters.SYNC_FEATURE_PROMOTION_DISMISS_REASON, PIXEL_DISMISS_REASON_USER_TAPPED)
            },
        )
    }

    private suspend fun isPromoExhausted(): Boolean {
        val isDismissedByUser = promotionDataStore.hasPromoBeenDismissed(ChatTabPage)
        if (isDismissedByUser) {
            return true
        }

        val isImpressionCapReached = promotionDataStore.getPromoImpressionCount(ChatTabPage) >= MAX_IMPRESSION_COUNT
        if (isImpressionCapReached) {
            pixel.fire(
                pixel = SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED,
                parameters = pixelParmas {
                    put(SyncPixelParameters.SYNC_FEATURE_PROMOTION_DISMISS_REASON, PIXEL_DISMISS_REASON_IMPRESSION_CAP)
                },
                type = Unique(MAX_IMPRESSION_CAP_PIXEL_TAG),
            )
        }
        return isImpressionCapReached
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

    private fun pixelParmas(
        customParams: (MutableMap<String, String>.() -> Unit)? = null,
    ): Map<String, String> = buildMap {
        put(SyncPixelParameters.SYNC_FEATURE_PROMOTION_SOURCE, PIXEL_SOURCE)
        customParams?.invoke(this)
    }

    companion object {
        const val MAX_IMPRESSION_COUNT = 3

        private const val PIXEL_SOURCE = "ai_chat"
        private const val PIXEL_DISMISS_REASON_USER_TAPPED = "user_tapped"
        private const val PIXEL_DISMISS_REASON_IMPRESSION_CAP = "impression_cap"

        private val MAX_IMPRESSION_CAP_PIXEL_TAG = buildString {
            append(SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED.pixelName)
            append('_')
            append(PIXEL_SOURCE)
            append('_')
            append(PIXEL_DISMISS_REASON_IMPRESSION_CAP)
            append('_')
            append(MAX_IMPRESSION_COUNT)
        }
    }
}
