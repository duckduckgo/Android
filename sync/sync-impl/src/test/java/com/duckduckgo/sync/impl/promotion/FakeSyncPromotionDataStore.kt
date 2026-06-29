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

package com.duckduckgo.sync.impl.promotion

class FakeSyncPromotionDataStore : SyncPromotionDataStore {
    private val dismissMap = mutableMapOf<SyncPromotionDataStore.PromotionType, Boolean>()
    private val impressionsMap = mutableMapOf<SyncPromotionDataStore.PromotionType, Long>()

    override suspend fun hasPromoBeenDismissed(promotionType: SyncPromotionDataStore.PromotionType): Boolean {
        return dismissMap.getOrDefault(promotionType, false)
    }

    override suspend fun recordPromoDismissed(promotionType: SyncPromotionDataStore.PromotionType) {
        dismissMap[promotionType] = true
    }

    override suspend fun getPromoImpressionCount(promotionType: SyncPromotionDataStore.PromotionType): Long {
        return impressionsMap.getOrDefault(promotionType, 0L)
    }

    override suspend fun recordPromoImpression(promotionType: SyncPromotionDataStore.PromotionType) {
        impressionsMap[promotionType] = impressionsMap.getOrDefault(promotionType, 0) + 1
    }

    override suspend fun clearPromoHistory(promotionType: SyncPromotionDataStore.PromotionType) {
        dismissMap.clear()
        impressionsMap.clear()
    }
}
